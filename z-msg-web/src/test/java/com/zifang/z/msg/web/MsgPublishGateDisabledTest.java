package com.zifang.z.msg.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * publish 端点的门禁语义 —— <b>开关关</b>这一侧的回归钉（TASK-20261012-019）。
 * <p>
 * 这条门禁是 z-msg 最老的一处服务间入口，{@code POST /api/msg/publish} 的历史口径是
 * <b>HTTP 200 + 业务 code:403</b>（README「安全边界」那一节写着的）。新增 {@code POST /api/msg/send}
 * 时最容易带坏的就是它：新端点若改用真 HTTP 403，回头有人把两处的门禁语义「统一」，
 * publish 的调用方就会突然从 200 变成 403 —— 那是线上可见的行为变更。
 * <p>
 * 所以这里把「关着时 publish 长什么样」钉死：HTTP 200、正文业务码 403、<b>且一条消息都没发出去</b>
 * （最后这条是防「闸门形同虚设」的：只回一句 403 却照样把消息投递了，等于没关）。
 * <p>
 * 另一半（开关开 ⇒ 真投递）见 {@link MsgPublishGateEnabledTest}。两个类合起来才是 publish 的完整门禁语义。
 */
@SpringBootTest(classes = MsgWebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        // 与本模块其它 @SpringBootTest 一致：z-ctc-web 在本模块是 provided 作用域，
        // @EnableAutoConfiguration 会把它的自动配置拉进来 ⇒ 第二个 DataSource ⇒ 上下文起不来。
        // z-ctc-sso 那份 WebMvcConfigurer 还会把 /api/msg/** 全部按 401 拒掉。
        // 排掉它们建模的是「只装了 z-msg-web 的宿主」，与 provided 的契约一致。
        "spring.autoconfigure.exclude=com.zifang.ctc.sso.config.SsoAutoConfiguration,com.zifang.ctc.web.config.ZCompanyCtcWebAutoConfiguration",
        "z-msg.enabled=true",
        // 故意**不**设 z-msg.web.publish-endpoint-enabled：这一侧验的就是「缺省 = 关」。
        "z.msg.test.db=msg_pub_gate_off_it"
})
public class MsgPublishGateDisabledTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long PROBE_USER = 9101L;
    private static final String PROBE_EVENT = "MSG019-PROBE-OFF";

    @Autowired
    private TestRestTemplate rest;
    @jakarta.annotation.Resource(name = "dataSourceMsg")
    private DataSource dataSourceMsg;

    @Test
    public void publishWithGateOffAnswers403AndDeliversNothing() throws Exception {
        String url = "/api/msg/publish?eventType=" + PROBE_EVENT + "&userId=" + PROBE_USER;
        ResponseEntity<String> resp = exchange(url, "{\"k\":\"v\"}");

        System.out.println("===== PROBE publish/gate-off =====");
        System.out.println("REQUEST        : POST " + url);
        System.out.println("REQUEST-BODY   : {\"k\":\"v\"}");
        System.out.println("RESPONSE-STATUS: " + resp.getStatusCodeValue() + " " + resp.getStatusCode());
        System.out.println("RESPONSE-BODY  : " + resp.getBody());

        assertEquals(200, resp.getStatusCodeValue(),
                "publish 关着时的历史口径是 HTTP 200 + 业务 code:403，实际 " + resp.getStatusCode());
        JsonNode body = JSON.readTree(resp.getBody());
        assertEquals(403, body.path("code").asInt(),
                "业务码必须是 403，实际 body=" + resp.getBody());
        assertTrue(body.path("message").asText().contains("publish-endpoint-enabled"),
                "403 的说明里必须点名怎么打开它（z-msg.web.publish-endpoint-enabled），实际 body=" + resp.getBody());
        assertEquals(0, countMessages(PROBE_EVENT, PROBE_USER),
                "闸门关着时一条都不该发出去：真发出去的话这个闸门等于没关");

        System.out.println("ROWS z_msg_message(eventType=" + PROBE_EVENT + ", userId=" + PROBE_USER + "): "
                + countMessages(PROBE_EVENT, PROBE_USER));
        System.out.println("===== END PROBE publish/gate-off =====");
    }

    private ResponseEntity<String> exchange(String url, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<String>(body, headers), String.class);
    }

    private int countMessages(String eventType, long userId) {
        try (Connection c = dataSourceMsg.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM z_msg_message "
                     + "WHERE event_type = '" + eventType + "' AND user_id = " + userId)) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new AssertionError("读 z_msg_message 失败: " + e, e);
        }
    }
}
