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
 * publish 端点的门禁语义 —— <b>开关开</b>这一侧的回归钉（TASK-20261012-019）。
 * <p>
 * 与 {@link MsgPublishGateDisabledTest} 配对：那一侧钉「关着 = 拒」，这一侧钉「开着 = 真的发出去」。
 * 只有拒的那一半是危险的另一半（闸门永远关着，谁都没发现）；只有开的那一半同样危险
 * （闸门形同虚设，谁都能往任意 userId 的收件箱塞消息）。
 * <p>
 * 这里断言的不只是响应体：还断言消息<b>真的落进了 z_msg_message</b>（publish 走的是 MessageBus →
 * InAppChannel 这条路），否则一个只会回 "ok" 但什么都没发的实现也能骗过这条用例。
 */
@SpringBootTest(classes = MsgWebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=com.zifang.ctc.sso.config.SsoAutoConfiguration,com.zifang.ctc.web.config.ZCompanyCtcWebAutoConfiguration",
        "z-msg.enabled=true",
        "z-msg.web.publish-endpoint-enabled=true",
        "z.msg.test.db=msg_pub_gate_on_it"
})
public class MsgPublishGateEnabledTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long PROBE_USER = 9102L;
    private static final String PROBE_EVENT = "MSG019-PROBE-ON";

    @Autowired
    private TestRestTemplate rest;
    @jakarta.annotation.Resource(name = "dataSourceMsg")
    private DataSource dataSourceMsg;

    @Test
    public void publishWithGateOnDeliversForReal() throws Exception {
        String url = "/api/msg/publish?eventType=" + PROBE_EVENT + "&userId=" + PROBE_USER;
        ResponseEntity<String> resp = exchange(url, "{\"k\":\"v\"}");

        System.out.println("===== PROBE publish/gate-on =====");
        System.out.println("REQUEST        : POST " + url);
        System.out.println("REQUEST-BODY   : {\"k\":\"v\"}");
        System.out.println("RESPONSE-STATUS: " + resp.getStatusCodeValue() + " " + resp.getStatusCode());
        System.out.println("RESPONSE-BODY  : " + resp.getBody());

        assertEquals(200, resp.getStatusCodeValue(),
                "publish 开着时是 HTTP 200，实际 " + resp.getStatusCode() + " body=" + resp.getBody());
        JsonNode body = JSON.readTree(resp.getBody());
        assertEquals(200, body.path("code").asInt(),
                "业务码必须是 200，实际 body=" + resp.getBody());
        assertTrue(body.path("success").asBoolean(), "success 必须是 true，实际 body=" + resp.getBody());
        assertEquals("ok", body.path("data").asText(), "data 必须是 ok，实际 body=" + resp.getBody());

        int rows = countMessages(PROBE_EVENT, PROBE_USER);
        System.out.println("ROWS z_msg_message(eventType=" + PROBE_EVENT + ", userId=" + PROBE_USER + "): " + rows);
        System.out.println("===== END PROBE publish/gate-on =====");
        assertEquals(1, rows, "publish 开着时必须真的投递一条：只回 ok 却没落库 = 闸门形同虚设");
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
