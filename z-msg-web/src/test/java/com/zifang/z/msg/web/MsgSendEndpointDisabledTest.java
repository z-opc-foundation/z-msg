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
 * {@code POST /api/msg/send} 的<b>开关关</b>这一侧（TASK-20261012-019）。
 * <p>
 * 这个端点能往<b>任意</b>收件人发消息 —— 和 publish 一样，是个未认证的投递入口。
 * 所以默认必须关，关着的时候必须回<b>真 HTTP 403</b>（不是 404、不是 200 + 业务码）。
 * <p>
 * 用例钉四件事：
 * <ol>
 *   <li>HTTP 状态码是 403（不是 404 —— 404 会让调用方以为端点不存在从而另寻他路，
 *       200 + 业务码更糟：绝大多数 HTTP 客户端只看状态码）；</li>
 *   <li>正文业务码也是 403，且说明了怎么打开它；</li>
 *   <li><b>一条消息都没发出去</b>（闸门形同虚设的防线）；</li>
 *   <li>缺省配置下开关就是关的（不设任何属性也必须是 403 —— 属性绑定写错的话这里会红）。</li>
 * </ol>
 */
@SpringBootTest(classes = MsgWebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=com.zifang.ctc.sso.config.SsoAutoConfiguration,com.zifang.ctc.web.config.ZCompanyCtcWebAutoConfiguration",
        "z-msg.enabled=true",
        // 故意**不**设 z-msg.web.send-endpoint-enabled：这一侧验的就是「缺省 = 关」。
        "z.msg.test.db=msg_send_off_it"
})
public class MsgSendEndpointDisabledTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long PROBE_USER = 9201L;
    private static final String PROBE_BIZ = "MSG019-SEND-OFF";

    @Autowired
    private TestRestTemplate rest;
    @jakarta.annotation.Resource(name = "dataSourceMsg")
    private DataSource dataSourceMsg;

    @Test
    public void sendWithGateOffAnswers403AndDeliversNothing() throws Exception {
        String body = "{\"channel\":\"IN_APP\",\"userId\":" + PROBE_USER + ","
                + "\"subject\":\"闸门关着\",\"content\":\"这条不该发出去\","
                + "\"bizType\":\"" + PROBE_BIZ + "\"}";
        ResponseEntity<String> resp = exchange(body);

        System.out.println("===== PROBE send/gate-off =====");
        System.out.println("REQUEST        : POST /api/msg/send");
        System.out.println("REQUEST-BODY   : " + body);
        System.out.println("RESPONSE-STATUS: " + resp.getStatusCodeValue() + " " + resp.getStatusCode());
        System.out.println("RESPONSE-BODY  : " + resp.getBody());

        assertEquals(403, resp.getStatusCodeValue(),
                "开关关着必须是**真 HTTP 403**（不是 404、不是 200 + 业务码），实际 "
                        + resp.getStatusCode() + " body=" + resp.getBody());
        JsonNode json = JSON.readTree(resp.getBody());
        assertEquals(403, json.path("code").asInt(),
                "正文业务码也必须是 403，实际 body=" + resp.getBody());
        assertTrue(json.path("message").asText().contains("send-endpoint-enabled"),
                "403 的说明里必须点名怎么打开它（z-msg.web.send-endpoint-enabled），实际 body=" + resp.getBody());
        assertEquals(0, countMessages(PROBE_BIZ, PROBE_USER),
                "闸门关着时一条都不该发出去：真发出去的话这个闸门等于没关");

        System.out.println("ROWS z_msg_message(bizType=" + PROBE_BIZ + ", userId=" + PROBE_USER + "): "
                + countMessages(PROBE_BIZ, PROBE_USER));
        System.out.println("===== END PROBE send/gate-off =====");
    }

    /**
     * 闸门关着时，<b>不合法的 body 也只回 403</b>：端点关着的时候，
     * 连"你的 body 合不合法"都不该告诉外人。
     */
    @Test
    public void sendWithGateOffDoesNotLeakValidationDetail() throws Exception {
        ResponseEntity<String> resp = exchange("{\"channel\":\"NOT_A_CHANNEL\",\"content\":\"\"}");

        System.out.println("===== PROBE send/gate-off + 非法 body =====");
        System.out.println("REQUEST        : POST /api/msg/send");
        System.out.println("REQUEST-BODY   : {\"channel\":\"NOT_A_CHANNEL\",\"content\":\"\"}");
        System.out.println("RESPONSE-STATUS: " + resp.getStatusCodeValue() + " " + resp.getStatusCode());
        System.out.println("RESPONSE-BODY  : " + resp.getBody());

        assertEquals(403, resp.getStatusCodeValue(),
                "闸门关着时非法 body 也必须是 403（不能因为 body 不合法就回 400 把端点形状漏出去），实际 "
                        + resp.getStatusCode() + " body=" + resp.getBody());
        System.out.println("===== END PROBE send/gate-off + 非法 body =====");
    }

    private ResponseEntity<String> exchange(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        return rest.exchange("/api/msg/send", HttpMethod.POST,
                new HttpEntity<String>(body, headers), String.class);
    }

    private int countMessages(String bizType, long userId) {
        try (Connection c = dataSourceMsg.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM z_msg_message "
                     + "WHERE event_type = '" + bizType + "' AND user_id = " + userId)) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new AssertionError("读 z_msg_message 失败: " + e, e);
        }
    }
}
