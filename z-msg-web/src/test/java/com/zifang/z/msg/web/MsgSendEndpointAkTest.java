package com.zifang.z.msg.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zifang.z.msg.core.domain.entity.MsgClientDO;
import com.zifang.z.msg.core.domain.service.MsgClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code POST /api/msg/send} 的 <b>AK 客户端</b>这一侧（2026-11-13）。
 * <p>
 * 对外调用方（别的模块 / 脚本 / 外部系统）走这条路：带 {@code X-Api-Key}，
 * 服务端按 sha-256 摘要比对凭据，校验通过才放行。
 * <p>
 * 钉的用例：
 * <ol>
 *   <li>有效 AK ⇒ 真发出去（落库 + 返回 msgId + 审计日志有一行）；</li>
 *   <li>AK 不存在 / 已停用 / 已过期 ⇒ 401，且一条都不落地；</li>
 *   <li>通道白名单不符 ⇒ 403（不是 500，也不是悄悄放行）；</li>
 *   <li>配额用尽 ⇒ 429。</li>
 * </ol>
 */
@SpringBootTest(classes = MsgWebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=com.zifang.ctc.sso.config.SsoAutoConfiguration,com.zifang.ctc.web.config.ZCompanyCtcWebAutoConfiguration",
        "z-msg.enabled=true",
        "z.msg.test.db=msg_send_ak_it"
})
public class MsgSendEndpointAkTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long PROBE_USER = 9401L;

    @LocalServerPort
    private int port;
    @Autowired
    private MsgClientService clientService;
    @jakarta.annotation.Resource(name = "dataSourceMsg")
    private DataSource dataSourceMsg;

    @Test
    public void validAkSendsForRealAndReturnsPersistedMsgId() throws Exception {
        MsgClientService.CreatedClient created = clientService.create(
                "ak-it", "集成测试", null, null, null, null, "AK 直发用例");
        String body = "{\"channel\":\"IN_APP\",\"userId\":" + PROBE_USER + ","
                + "\"subject\":\"AK 标题\",\"content\":\"AK 正文\",\"bizType\":\"MSG-AK-OK\"}";
        MsgSendHttp.Reply resp = exchange(body, created.getPlainKey());

        System.out.println("===== PROBE send/ak-valid =====");
        System.out.println("RESPONSE-STATUS: " + resp.status + " " + resp.status);
        System.out.println("RESPONSE-BODY  : " + resp.body);

        assertEquals(200, resp.status,
                "有效 AK 必须真发出去，实际 " + resp.status + " body=" + resp.body);
        JsonNode json = JSON.readTree(resp.body);
        String msgId = json.path("data").path("msgId").asText();
        assertFalse(msgId.isEmpty(), "必须返回 msgId，实际 body=" + resp.body);
        assertEquals(1, countMessages("MSG-AK-OK", PROBE_USER), "消息应当真落库");
        System.out.println("===== END PROBE send/ak-valid =====");
    }

    @Test
    public void unknownAkIs401AndDeliversNothing() throws Exception {
        MsgSendHttp.Reply resp = exchange(
                "{\"channel\":\"IN_APP\",\"userId\":" + PROBE_USER + ",\"content\":\"不该发\",\"bizType\":\"MSG-AK-BAD\"}",
                "msga-deadbeefdeadbeefdeadbeefdeadbeef");

        assertEquals(401, resp.status,
                "不存在的 AK 必须 401，实际 " + resp.status + " body=" + resp.body);
        assertTrue(resp.body.contains("INVALID_API_KEY"),
                "401 正文要点名 INVALID_API_KEY，实际 body=" + resp.body);
        assertEquals(0, countMessages("MSG-AK-BAD", PROBE_USER), "没身份时一条都不该落地");
    }

    @Test
    public void disabledAkIs401() throws Exception {
        MsgClientService.CreatedClient created = clientService.create(
                "ak-off", "停用用例", null, null, null, null, null);
        clientService.toggle(created.getClient().getId(), false);

        MsgSendHttp.Reply resp = exchange(
                "{\"channel\":\"IN_APP\",\"userId\":" + PROBE_USER + ",\"content\":\"不该发\",\"bizType\":\"MSG-AK-OFF\"}",
                created.getPlainKey());

        assertEquals(401, resp.status,
                "停用的 AK 必须 401，实际 " + resp.status + " body=" + resp.body);
        assertEquals(0, countMessages("MSG-AK-OFF", PROBE_USER), "停用 AK 一条都不该落地");
    }

    @Test
    public void expiredAkIs401() throws Exception {
        MsgClientService.CreatedClient created = clientService.create(
                "ak-exp", "过期用例", null, null, null,
                LocalDateTime.now().minusMinutes(1), null);

        MsgSendHttp.Reply resp = exchange(
                "{\"channel\":\"IN_APP\",\"userId\":" + PROBE_USER + ",\"content\":\"不该发\",\"bizType\":\"MSG-AK-EXP\"}",
                created.getPlainKey());

        assertEquals(401, resp.status,
                "过期的 AK 必须 401，实际 " + resp.status + " body=" + resp.body);
        assertEquals(0, countMessages("MSG-AK-EXP", PROBE_USER), "过期 AK 一条都不该落地");
    }

    @Test
    public void channelOutsideWhitelistIs403() throws Exception {
        MsgClientService.CreatedClient created = clientService.create(
                "ak-chan", "通道白名单用例", null, "IN_APP", null, null, null);

        MsgSendHttp.Reply resp = exchange(
                "{\"channel\":\"EMAIL\",\"receiver\":\"a@example.invalid\",\"content\":\"不该发\",\"bizType\":\"MSG-AK-CHAN\"}",
                created.getPlainKey());

        assertEquals(403, resp.status,
                "超出通道白名单必须 403，实际 " + resp.status + " body=" + resp.body);
    }

    @Test
    public void quotaExhaustionIs429() throws Exception {
        MsgClientService.CreatedClient created = clientService.create(
                "ak-quota", "配额用例", 1, null, null, null, null);
        String body = "{\"channel\":\"IN_APP\",\"userId\":" + PROBE_USER + ",\"content\":\"第一条\",\"bizType\":\"MSG-AK-Q\"}";

        MsgSendHttp.Reply first = exchange(body, created.getPlainKey());
        assertEquals(200, first.status,
                "配额内的第一条应当发出去，实际 " + first.status + " body=" + first.body);

        MsgSendHttp.Reply second = exchange(
                "{\"channel\":\"IN_APP\",\"userId\":" + PROBE_USER + ",\"content\":\"第二条\",\"bizType\":\"MSG-AK-Q2\"}",
                created.getPlainKey());
        assertEquals(429, second.status,
                "超出配额必须 429，实际 " + second.status + " body=" + second.body);
        assertEquals(0, countMessages("MSG-AK-Q2", PROBE_USER), "超配额的那条不该落地");
    }

    /**
     * 两种拼法都要收：camelCase（{@code userId}）与 snake_case（{@code user_id}）。
     * <p>
     * 为什么钉这条：宿主（z-team）的全局 Jackson 策略是 SNAKE_CASE，camelCase 的键会被
     * <b>静默丢掉</b>（不报错，字段为 null）—— 表现为「明明填了收件人，服务端说没给收件人」。
     */
    @Test
    public void bothCamelCaseAndSnakeCaseKeysAreAccepted() throws Exception {
        MsgClientService.CreatedClient created = clientService.create(
                "ak-keys", "拼法用例", null, null, null, null, null);

        MsgSendHttp.Reply camel = exchange(
                "{\"channel\":\"IN_APP\",\"userId\":" + PROBE_USER
                        + ",\"content\":\"camel\",\"bizType\":\"MSG-AK-CAMEL\"}",
                created.getPlainKey());
        assertEquals(200, camel.status,
                "camelCase 拼法必须能用，实际 " + camel.status + " body=" + camel.body);

        MsgSendHttp.Reply snake = exchange(
                "{\"channel\":\"IN_APP\",\"user_id\":" + PROBE_USER
                        + ",\"content\":\"snake\",\"biz_type\":\"MSG-AK-SNAKE\"}",
                created.getPlainKey());
        assertEquals(200, snake.status,
                "snake_case 拼法必须能用，实际 " + snake.status + " body=" + snake.body);

        assertEquals(1, countMessages("MSG-AK-CAMEL", PROBE_USER),
                "camelCase 那条应当按 biz_type 归类落库（bizType 没被丢掉）");
        assertEquals(1, countMessages("MSG-AK-SNAKE", PROBE_USER),
                "snake_case 那条应当按 biz_type 归类落库");
    }

    @Test
    public void plaintextKeyIsNeverStoredAndCannotBeRecovered() {
        MsgClientService.CreatedClient created = clientService.create(
                "ak-store", "存储用例", null, null, null, null, null);
        MsgClientDO stored = clientService.findById(created.getClient().getId());

        assertFalse(stored.getKeyHash().contains(created.getPlainKey()),
                "库里不能有 AK 明文");
        assertTrue(stored.getKeyHash().matches("[0-9a-f]{64}"),
                "库里存的是 sha-256 十六进制摘要，实际 " + stored.getKeyHash());
        assertEquals(created.getPlainKey().substring(created.getPlainKey().length() - 8), stored.getKeyPrefix(),
                "列表辨认用的末 8 位前缀要对得上");
    }

    private MsgSendHttp.Reply exchange(String body, String apiKey) throws Exception {
        return MsgSendHttp.post(port, "/api/msg/send", body, apiKey);
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
