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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code POST /api/msg/send} 的<b>开关开</b>这一侧（TASK-20261012-019）。
 * <p>
 * 这一侧验的是「开关打开之后，它是不是真的能把消息发出去」，而且是真发：
 * 跑在真 H2 + 真 Tomcat 上，走的是 {@code MessageGateway → ChannelRouter → InAppChannel} 那条真管道，
 * 落库的是 {@code z_msg_message}，审计落的是 {@code z_msg_delivery_log}。
 * <p>
 * 四条线各有用例：
 * <ol>
 *   <li><b>透传</b>：请求体里的 subject / content / linkUrl / userId / bizType 原样落到下游那一行，
 *       返回的 msgId 就是那一行的 msg_id（不是一个跟落库对不上的临时串）；</li>
 *   <li><b>开关语义</b>：开着就通（200 + msgId），关着就 403（另一半在
 *       {@link MsgSendEndpointDisabledTest}）；</li>
 *   <li><b>校验分支</b>：三个非法 body 各 4xx，且错误信息说人话；</li>
 *   <li><b>审计</b>：投递日志里有一行，provider_message_id 就是返回的 msgId。</li>
 * </ol>
 */
@SpringBootTest(classes = MsgWebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=com.zifang.ctc.sso.config.SsoAutoConfiguration,com.zifang.ctc.web.config.ZCompanyCtcWebAutoConfiguration",
        "z-msg.enabled=true",
        "z-msg.web.send-endpoint-enabled=true",
        // 只为了最后一步「消息真的出现在收件箱里」——那个列表端点要身份
        "z-msg.web.trusted-header-enabled=true",
        "z.msg.test.db=msg_send_on_it"
})
public class MsgSendEndpointEnabledTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long PROBE_USER = 9301L;
    private static final String PROBE_BIZ = "MSG019-SEND-ON";

    @Autowired
    private TestRestTemplate rest;
    @javax.annotation.Resource(name = "dataSourceMsg")
    private DataSource dataSourceMsg;

    // ---------------- 透传 + 真实发送 + 审计 ----------------

    @Test
    public void sendDeliversForRealAndReturnsTheMsgIdThatWasPersisted() throws Exception {
        String body = "{\"channel\":\"IN_APP\",\"userId\":" + PROBE_USER + ","
                + "\"subject\":\"直发标题\",\"content\":\"直发正文\","
                + "\"linkUrl\":\"https://example.invalid/msg/1\","
                + "\"bizType\":\"" + PROBE_BIZ + "\"}";
        ResponseEntity<String> resp = exchange(body);

        System.out.println("===== PROBE send/gate-on =====");
        System.out.println("REQUEST        : POST /api/msg/send");
        System.out.println("REQUEST-BODY   : " + body);
        System.out.println("RESPONSE-STATUS: " + resp.getStatusCodeValue() + " " + resp.getStatusCode());
        System.out.println("RESPONSE-BODY  : " + resp.getBody());

        assertEquals(200, resp.getStatusCodeValue(),
                "开关开着必须真的发出去，实际 " + resp.getStatusCode() + " body=" + resp.getBody());
        JsonNode json = JSON.readTree(resp.getBody());
        assertTrue(json.path("success").asBoolean(), "success 必须是 true，实际 body=" + resp.getBody());
        String msgId = json.path("data").path("msgId").asText();
        assertFalse(msgId.isEmpty(), "必须返回 msgId，实际 body=" + resp.getBody());

        // 透传：请求体里的字段原样落到下游那一行
        Map<String, Object> row = oneRow("SELECT * FROM z_msg_message WHERE msg_id = '" + msgId + "'");
        assertNotNull(row, "下游 z_msg_message 里应当有 msg_id=" + msgId + " 这一行（没落库 = 没真发）");
        System.out.println("ROW z_msg_message: " + row);
        assertEquals("直发标题", row.get("title"), "subject 应当原样透传");
        assertEquals("直发正文", row.get("content"), "content 应当原样透传");
        assertEquals("https://example.invalid/msg/1", row.get("link_url"), "linkUrl 应当原样透传");
        assertEquals(PROBE_BIZ, row.get("event_type"), "bizType 应当原样透传");
        assertEquals(PROBE_USER, asLong(row.get("user_id")), "userId 应当原样透传");

        // 审计：投递日志有一行，provider_message_id 就是返回的 msgId
        Map<String, Object> log = oneRow("SELECT * FROM z_msg_delivery_log "
                + "WHERE provider_message_id = '" + msgId + "'");
        assertNotNull(log, "投递日志里应当有 provider_message_id=" + msgId + " 这一行（审计没落 = 端点绕过了管道）");
        System.out.println("ROW z_msg_delivery_log: " + log);
        assertEquals(1, asLong(log.get("status")), "投递日志状态应是 1=成功");
        assertEquals("IN_APP", log.get("channel"), "投递日志通道应是 IN_APP");
        assertEquals("db", log.get("provider"), "IN_APP 的 provider 应是 db");

        // 真的到了收件箱（下游效果，不是只回了个 msgId）
        List<String> inbox = inboxTitles(PROBE_USER);
        System.out.println("INBOX titles(userId=" + PROBE_USER + "): " + inbox);
        assertTrue(inbox.contains("直发标题"), "刚直发的消息应当出现在收件箱里，实际 " + inbox);

        System.out.println("===== END PROBE send/gate-on =====");
    }

    /**
     * 两次直发 ⇒ 两个不同的 msgId（msgId 是服务端生成的，不该被调用方复用/串号）。
     */
    @Test
    public void eachSendGetsItsOwnMsgId() throws Exception {
        String a = sendOne("{\"channel\":\"IN_APP\",\"userId\":" + PROBE_USER
                + ",\"content\":\"甲\",\"bizType\":\"" + PROBE_BIZ + "-A\"}");
        String b = sendOne("{\"channel\":\"IN_APP\",\"userId\":" + PROBE_USER
                + ",\"content\":\"乙\",\"bizType\":\"" + PROBE_BIZ + "-B\"}");
        System.out.println("===== PROBE send/two-msgids =====");
        System.out.println("msgId#1: " + a);
        System.out.println("msgId#2: " + b);
        System.out.println("===== END PROBE send/two-msgids =====");
        assertFalse(a.isEmpty() && b.isEmpty(), "两次直发都要有 msgId");
        assertFalse(a.equals(b), "两次直发的 msgId 必须不同，实际两次都是 " + a);
    }

    // ---------------- 校验分支：三个非法 body ----------------

    @Test
    public void threeIllegalBodiesEachAnswer4xxWithReadableMessage() throws Exception {
        checkIllegal("非法 channel", "{\"channel\":\"NOT_A_CHANNEL\",\"userId\":" + PROBE_USER
                + ",\"content\":\"x\",\"bizType\":\"" + PROBE_BIZ + "-E1\"}");
        checkIllegal("receiver 与 userId 都没有", "{\"channel\":\"IN_APP\","
                + "\"content\":\"x\",\"bizType\":\"" + PROBE_BIZ + "-E2\"}");
        checkIllegal("content 为空", "{\"channel\":\"IN_APP\",\"userId\":" + PROBE_USER
                + ",\"content\":\"   \",\"bizType\":\"" + PROBE_BIZ + "-E3\"}");
    }

    private void checkIllegal(String label, String body) throws Exception {
        ResponseEntity<String> resp = exchange(body);
        System.out.println("===== PROBE send/非法 body · " + label + " =====");
        System.out.println("REQUEST        : POST /api/msg/send");
        System.out.println("REQUEST-BODY   : " + body);
        System.out.println("RESPONSE-STATUS: " + resp.getStatusCodeValue() + " " + resp.getStatusCode());
        System.out.println("RESPONSE-BODY  : " + resp.getBody());

        assertTrue(resp.getStatusCodeValue() >= 400 && resp.getStatusCodeValue() < 500,
                label + " 必须 4xx，实际 " + resp.getStatusCode() + " body=" + resp.getBody());
        JsonNode json = JSON.readTree(resp.getBody());
        String msg = json.path("message").asText();
        assertFalse(msg.isEmpty(), label + " 的错误信息不能为空");
        assertTrue(!msg.equals("处理失败") && msg.length() > "处理失败|".length(),
                label + " 的错误信息要能读懂（不能只有一句『处理失败』），实际 body=" + resp.getBody());
        System.out.println("===== END PROBE send/非法 body · " + label + " =====");
    }

    // ---------------- helpers ----------------

    private String sendOne(String body) throws Exception {
        ResponseEntity<String> resp = exchange(body);
        assertEquals(200, resp.getStatusCodeValue(), "直发应当成功，实际 " + resp.getBody());
        return JSON.readTree(resp.getBody()).path("data").path("msgId").asText();
    }

    private ResponseEntity<String> exchange(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        return rest.exchange("/api/msg/send", HttpMethod.POST,
                new HttpEntity<String>(body, headers), String.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> oneRow(String sql) {
        try (Connection c = dataSourceMsg.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            java.sql.ResultSetMetaData md = rs.getMetaData();
            Map<String, Object> row = new java.util.LinkedHashMap<String, Object>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                // H2 的列标签默认大写，这里统一小写；正文列是 CLOB，
                // getObject 会拿到一个 Clob 对象（打印出来是 "clob0: ..."），
                // 所以一律按字符串读，断言那边再按需要转数字。
                row.put(md.getColumnLabel(i).toLowerCase(), rs.getString(i));
            }
            return row;
        } catch (Exception e) {
            throw new AssertionError("查库失败: " + sql + " ⇒ " + e, e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> inboxTitles(long userId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Msg-User-Id", String.valueOf(userId));
        headers.set("Content-Type", "application/json");
        ResponseEntity<String> resp = rest.exchange("/api/msg/inbox/list?page=1&size=50",
                HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
        assertEquals(200, resp.getStatusCodeValue(), "收件箱列表应当 200，实际 " + resp.getBody());
        JsonNode records = JSON.readTree(resp.getBody()).path("data").path("records");
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (JsonNode r : records) {
            out.add(r.path("title").asText());
        }
        return out;
    }

    private static long asLong(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return Long.parseLong(String.valueOf(v).trim());
    }
}
