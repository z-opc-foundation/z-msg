package com.zifang.z.msg.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code POST /api/msg/open/send} —— 对外（机器对机器）发送端点（2026-11-14）。
 * <p>
 * 这条路是<b>独立于平台端点</b>的第二扇门：宿主 SSO 闸门只认平台用户，
 * 机器调用方（别的板块 / z-script 脚本 / 外部系统）没有用户态，
 * 所以这组端点用 {@code @NoLogin} 豁免闸门，鉴别由 AK 自己兜。
 * <p>
 * 钉的用例：
 * <ol>
 *   <li>有效 AK ⇒ 真发出去（落库 + 返回 msgId）；</li>
 *   <li><b>没有 AK ⇒ 401</b>（豁免了闸门之后，这条路唯一的鉴别就是 AK，不许匿名放行）；</li>
 *   <li>AK 不存在 / 停用 ⇒ 401，且一条都不落地；</li>
 *   <li>通道白名单不符 ⇒ 403；</li>
 *   <li>{@code GET /api/msg/open/client/me} 回这份 AK 自己的单子（能走哪条通道、还剩几条）。</li>
 * </ol>
 */
@SpringBootTest(classes = MsgWebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=com.zifang.ctc.sso.config.SsoAutoConfiguration,com.zifang.ctc.web.config.ZCompanyCtcWebAutoConfiguration",
        "z-msg.enabled=true",
        "z.msg.test.db=msg_open_send_it"
})
public class MsgOpenSendEndpointTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long PROBE_USER = 9501L;

    @LocalServerPort
    private int port;
    @Autowired
    private MsgClientService clientService;
    @jakarta.annotation.Resource(name = "dataSourceMsg")
    private DataSource dataSourceMsg;

    @Test
    public void validAkSendsForReal() throws Exception {
        MsgClientService.CreatedClient created = clientService.create(
                "open-it", "对外用例", null, "IN_APP", null, null, "对外发送用例");
        String body = "{\"channel\":\"IN_APP\",\"user_id\":" + PROBE_USER
                + ",\"subject\":\"对外标题\",\"content\":\"对外正文\",\"biz_type\":\"MSG-OPEN-OK\"}";

        MsgSendHttp.Reply resp = MsgSendHttp.post(port, "/api/msg/open/send", body, created.getPlainKey());

        assertEquals(200, resp.status,
                "有效 AK 必须真发出去，实际 " + resp.status + " body=" + resp.body);
        JsonNode json = JSON.readTree(resp.body);
        assertFalse(json.path("data").path("msgId").asText().isEmpty(),
                "必须返回 msgId，实际 body=" + resp.body);
        assertEquals(1, countMessages("MSG-OPEN-OK"), "消息应当真落库");
    }

    @Test
    public void withoutApiKeyIs401AndDeliversNothing() throws Exception {
        String body = "{\"channel\":\"IN_APP\",\"user_id\":" + PROBE_USER
                + ",\"content\":\"不该发\",\"biz_type\":\"MSG-OPEN-NOKEY\"}";

        MsgSendHttp.Reply resp = MsgSendHttp.post(port, "/api/msg/open/send", body, null);

        assertEquals(401, resp.status,
                "对外端点豁免了登录闸门 ⇒ 没 AK 必须 401，实际 " + resp.status + " body=" + resp.body);
        assertTrue(resp.body.contains("X-Api-Key"),
                "401 正文要点名 X-Api-Key，实际 body=" + resp.body);
        assertEquals(0, countMessages("MSG-OPEN-NOKEY"), "没身份时一条都不该落地");
    }

    @Test
    public void unknownApiKeyIs401() throws Exception {
        MsgSendHttp.Reply resp = MsgSendHttp.post(port, "/api/msg/open/send",
                "{\"channel\":\"IN_APP\",\"user_id\":" + PROBE_USER + ",\"content\":\"不该发\"}",
                "msga-deadbeefdeadbeefdeadbeefdeadbeef");

        assertEquals(401, resp.status,
                "不存在的 AK 必须 401，实际 " + resp.status + " body=" + resp.body);
    }

    @Test
    public void disabledApiKeyIs401() throws Exception {
        MsgClientService.CreatedClient created = clientService.create(
                "open-off", "停用用例", null, null, null, null, null);
        clientService.toggle(created.getClient().getId(), false);

        MsgSendHttp.Reply resp = MsgSendHttp.post(port, "/api/msg/open/send",
                "{\"channel\":\"IN_APP\",\"user_id\":" + PROBE_USER + ",\"content\":\"不该发\"}",
                created.getPlainKey());

        assertEquals(401, resp.status,
                "停用的 AK 必须 401，实际 " + resp.status + " body=" + resp.body);
    }

    @Test
    public void channelOutsideWhitelistIs403() throws Exception {
        MsgClientService.CreatedClient created = clientService.create(
                "open-chan", "通道白名单用例", null, "IN_APP", null, null, null);

        MsgSendHttp.Reply resp = MsgSendHttp.post(port, "/api/msg/open/send",
                "{\"channel\":\"EMAIL\",\"receiver\":\"a@example.invalid\",\"content\":\"不该发\"}",
                created.getPlainKey());

        assertEquals(403, resp.status,
                "超出通道白名单必须 403，实际 " + resp.status + " body=" + resp.body);
    }

    @Test
    public void clientMeReturnsOwnProfile() throws Exception {
        MsgClientService.CreatedClient created = clientService.create(
                "open-me", "自省用例", 50, "IN_APP", null, null, null);

        MsgSendHttp.Reply resp = MsgSendHttp.get(port, "/api/msg/open/client/me", created.getPlainKey());

        assertEquals(200, resp.status,
                "有效 AK 必须拿到自己的单子，实际 " + resp.status + " body=" + resp.body);
        JsonNode data = JSON.readTree(resp.body).path("data");
        assertEquals("open-me", data.path("appCode").asText(), "appCode 应当对得上");
        assertEquals(50, data.path("dailyQuota").asInt(), "日配额应当回显");
        assertTrue(data.path("allowedChannels").asText().contains("IN_APP"),
                "允许的通道应当回显，实际 body=" + resp.body);
    }

    @Test
    public void clientMeWithoutApiKeyIs401() throws Exception {
        MsgSendHttp.Reply resp = MsgSendHttp.get(port, "/api/msg/open/client/me", null);

        assertEquals(401, resp.status,
                "没 AK 时自省端点也必须 401，实际 " + resp.status + " body=" + resp.body);
    }

    private int countMessages(String bizType) {
        try (Connection c = dataSourceMsg.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM z_msg_message "
                     + "WHERE event_type = '" + bizType + "'")) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new AssertionError("读 z_msg_message 失败: " + e, e);
        }
    }
}
