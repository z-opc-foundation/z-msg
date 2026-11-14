package com.zifang.z.msg.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code POST /api/msg/send} 的<b>无身份</b>这一侧。
 * <p>
 * 这个端点能往<b>任意</b>收件人发消息 —— 未认证的投递入口不能开。
 * <p>
 * 准入口径（2026-11-13 起）：<b>AK 客户端</b>（{@code X-Api-Key}）或<b>平台用户身份</b>
 * （宿主注入的 {@code MsgPrincipalResolver}）都能发；两者都没有 ⇒ HTTP 401。
 * 老开关 {@code z-msg.web.send-endpoint-enabled=true} 仍然认，它会把裸调用也放行
 * （向后兼容），但缺省是关的。
 * <p>
 * 用例钉四件事：
 * <ol>
 *   <li>HTTP 状态码是 401（不是 404 —— 404 会让调用方以为端点不存在从而另寻他路，
 *       200 + 业务码更糟：绝大多数 HTTP 客户端只看状态码）；</li>
 *   <li>正文业务码也是 401，且说明了两条准入方式（AK / 平台登录态）；</li>
 *   <li><b>一条消息都没发出去</b>（闸门形同虚设的防线）；</li>
 *   <li>缺省配置下不设任何属性也必须是 401 —— 属性绑定写错的话这里会红。</li>
 * </ol>
 */
@SpringBootTest(classes = MsgWebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=com.zifang.ctc.sso.config.SsoAutoConfiguration,com.zifang.ctc.web.config.ZCompanyCtcWebAutoConfiguration",
        "z-msg.enabled=true",
        // 故意**不**设 z-msg.web.send-endpoint-enabled：这一侧验的就是「缺省 = 不放行」。
        "z.msg.test.db=msg_send_off_it"
})
public class MsgSendEndpointDisabledTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long PROBE_USER = 9201L;
    private static final String PROBE_BIZ = "MSG019-SEND-OFF";

    @LocalServerPort
    private int port;
    @jakarta.annotation.Resource(name = "dataSourceMsg")
    private DataSource dataSourceMsg;

    @Test
    public void sendWithoutIdentityAnswers401AndDeliversNothing() throws Exception {
        String body = "{\"channel\":\"IN_APP\",\"userId\":" + PROBE_USER + ","
                + "\"subject\":\"闸门关着\",\"content\":\"这条不该发出去\","
                + "\"bizType\":\"" + PROBE_BIZ + "\"}";
        MsgSendHttp.Reply resp = exchange(body);

        System.out.println("===== PROBE send/no-identity =====");
        System.out.println("REQUEST        : POST /api/msg/send");
        System.out.println("REQUEST-BODY   : " + body);
        System.out.println("RESPONSE-STATUS: " + resp.status + " " + resp.status);
        System.out.println("RESPONSE-BODY  : " + resp.body);

        assertEquals(401, resp.status,
                "没有 AK 也没有平台身份必须是**真 HTTP 401**（不是 404、不是 200 + 业务码），实际 "
                        + resp.status + " body=" + resp.body);
        JsonNode json = JSON.readTree(resp.body);
        assertEquals(401, json.path("code").asInt(),
                "正文业务码也必须是 401，实际 body=" + resp.body);
        String msg = json.path("message").asText();
        assertTrue(msg.contains("X-Api-Key") && msg.contains("登录态"),
                "401 的说明里必须点名两条准入方式（X-Api-Key / 平台登录态），实际 body=" + resp.body);
        assertEquals(0, countMessages(PROBE_BIZ, PROBE_USER),
                "没身份时一条都不该发出去：真发出去的话这个闸门等于没关");

        System.out.println("ROWS z_msg_message(bizType=" + PROBE_BIZ + ", userId=" + PROBE_USER + "): "
                + countMessages(PROBE_BIZ, PROBE_USER));
        System.out.println("===== END PROBE send/no-identity =====");
    }

    /**
     * 没身份时，<b>不合法的 body 也只回 401</b>：不准入的时候，
     * 连"你的 body 合不合法"都不该告诉外人。
     */
    @Test
    public void sendWithoutIdentityDoesNotLeakValidationDetail() throws Exception {
        MsgSendHttp.Reply resp = exchange("{\"channel\":\"NOT_A_CHANNEL\",\"content\":\"\"}");

        System.out.println("===== PROBE send/no-identity + 非法 body =====");
        System.out.println("REQUEST        : POST /api/msg/send");
        System.out.println("REQUEST-BODY   : {\"channel\":\"NOT_A_CHANNEL\",\"content\":\"\"}");
        System.out.println("RESPONSE-STATUS: " + resp.status + " " + resp.status);
        System.out.println("RESPONSE-BODY  : " + resp.body);

        assertEquals(401, resp.status,
                "没身份时非法 body 也必须是 401（不能因为 body 不合法就回 400 把端点形状漏出去），实际 "
                        + resp.status + " body=" + resp.body);
        System.out.println("===== END PROBE send/no-identity + 非法 body =====");
    }

    private MsgSendHttp.Reply exchange(String body) throws Exception {
        return MsgSendHttp.post(port, "/api/msg/send", body, null);
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
