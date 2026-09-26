package com.zifang.z.msg.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zifang.z.msg.web.config.AdminEndpointGate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理面默认关：模板 / 批量 / 投递日志三组前缀在没显式打开开关时一律 HTTP 403。
 * <p>
 * 本类刻意<b>不</b>声明 {@code z-msg.web.admin-endpoints-enabled}，所以它量的是"缺省值"这件事本身。
 * 每条 403 都配了对照：同一时刻非管理面端点必须照常答（{@link #openEndpointsKeepAnsweringWhileAdminIsBlocked()}），
 * 而在 {@link MsgAdminEndpointEnabledTest} 里同一个请求打开开关就要 200 ——
 * 否则"路由压根没挂上"也能让这一堆断言全绿。
 */
@SpringBootTest(classes = MsgWebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "z-msg.enabled=true",
        "z-msg.web.trusted-header-enabled=true",
        "z.msg.test.db=msg_admin_gate_it"
})
public class MsgAdminEndpointGateTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long USER = 7001L;

    @Autowired
    private TestRestTemplate rest;

    @Test
    public void adminSurfacesAreBlockedByDefault() {
        assertBlocked(HttpMethod.POST, "/api/msg/template/list");
        assertBlocked(HttpMethod.POST, "/api/msg/template");          // 建模板：裸前缀也得拦
        assertBlocked(HttpMethod.GET, "/api/msg/template/7");
        assertBlocked(HttpMethod.POST, "/api/msg/template/7/approve");
        assertBlocked(HttpMethod.POST, "/api/msg/template/cache/refresh");
        assertBlocked(HttpMethod.POST, "/api/msg/batch/submit");
        assertBlocked(HttpMethod.POST, "/api/msg/batch/list");
        assertBlocked(HttpMethod.POST, "/api/msg/delivery/list");
        // 这条挂在 MessageController 上，不在 MsgDeliveryLogController 里 —— 按 controller 挡就会漏
        assertBlocked(HttpMethod.GET, "/api/msg/delivery/stats");
    }

    /** 管理面关掉不该顺手把别的端点一起带走：拦截器挂在 /** 上，这条就是它的越界检查。 */
    @Test
    public void openEndpointsKeepAnsweringWhileAdminIsBlocked() {
        assertEquals(HttpStatus.OK, exchange(HttpMethod.GET, "/api/msg/channel/list", null).getStatusCode(),
                "通道自省不是管理面，不该被闸门挡住");

        ResponseEntity<String> mine = exchange(HttpMethod.GET, "/api/msg/inbox/unread-count", USER);
        assertEquals(HttpStatus.OK, mine.getStatusCode(), "自己的收件箱要照常答: " + mine.getBody());
        assertTrue(readTree(mine.getBody()).path("success").asBoolean(), mine.getBody());

        // 匿名 401 而不是 403：说明身份这道接缝仍在工作，闸门没有把鉴权语义盖成"未启用"
        assertEquals(HttpStatus.UNAUTHORIZED,
                exchange(HttpMethod.GET, "/api/msg/inbox/unread-count", null).getStatusCode(),
                "缺身份必须是 401，不能被管理面闸门改成 403");
    }

    /** 403 的理由要能被调用方读懂，否则宿主只会来问"为什么全挂了"。 */
    @Test
    public void rejectionSaysHowToTurnTheSurfaceOn() {
        ResponseEntity<String> resp = exchange(HttpMethod.POST, "/api/msg/template/list", null);
        JsonNode body = readTree(resp.getBody());
        assertFalse(body.path("success").asBoolean(), resp.getBody());
        assertEquals(403, body.path("code").asInt(), "HTTP 状态码与 Result.code 要一致: " + body);
        String message = body.path("message").asText();
        assertTrue(message.contains("z-msg.web.admin-endpoints-enabled"),
                "正文要点名那个开关: " + message);
        // Result.fail 会把状态码自带的那句拼在前面（实测 "处理失败|" + 我们的话），
        // 所以这里比的是后缀：闸门里那句话必须原样出现在调用方看到的正文里。
        assertTrue(message.endsWith(AdminEndpointGate.DISABLED_MESSAGE),
                "正文应与闸门里那句话逐字相同（改一处就得同时改测试）: " + message);
    }

    /** 前缀匹配按路径段对齐，不能是裸 startsWith —— 这条是普查那次判定的猎物。 */
    @Test
    public void prefixMatchingRespectsPathSegmentBoundaries() {
        assertTrue(AdminEndpointGate.isAdminPath("/api/msg/template"), "裸前缀本身就是管理面");
        assertTrue(AdminEndpointGate.isAdminPath("/api/msg/delivery/stats"));
        assertFalse(AdminEndpointGate.isAdminPath("/api/msg/templates"), "半截同名不算管理面");
        assertFalse(AdminEndpointGate.isAdminPath("/api/msg/batch-x/submit"), "同上");
        assertFalse(AdminEndpointGate.isAdminPath("/api/msg/inbox/list"));
        assertFalse(AdminEndpointGate.isAdminPath("/demo/notify"));
        assertFalse(AdminEndpointGate.isAdminPath(null), "属性缺失时不许抛，也不能当成管理面");
    }

    // ---------------- helpers ----------------

    private void assertBlocked(HttpMethod method, String path) {
        ResponseEntity<String> resp = exchange(method, path, null);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                method + " " + path + " 属于管理面，默认应当 403，实际 "
                        + resp.getStatusCode() + " body=" + resp.getBody());
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        if (userId != null) {
            headers.set("X-Msg-User-Id", String.valueOf(userId));
        }
        String body = HttpMethod.POST.equals(method) ? "{}" : null;
        return rest.exchange(path, method, new HttpEntity<String>(body, headers), String.class);
    }

    private static JsonNode readTree(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new AssertionError("响应不是 JSON: " + text, e);
        }
    }
}
