package com.zifang.z.msg.ws;

import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.api.RealtimeMessage;
import com.zifang.z.msg.api.RealtimeTopics;
import com.zifang.z.msg.api.TopicAuthorizationPolicy;
import com.zifang.z.msg.ws.support.LogCapture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code op=auth}：在**已经建立的连接上**换一张新票，重新证明身份（或换到另一个身份）。
 * <p>
 * 这个开关动的是"身份只在握手时定死"这条前提，所以每条判据都配了对照，而且刻意把
 * "失败必须什么都不改"放在最前面——最容易出的事故不是换不上，而是换失败顺手把一条
 * 好连接踢掉、或者把旧用户的红点漏给已经改名换姓的连接。
 * <p>
 * 关掉时（默认）的行为在 {@link MsgWsInbandAuthDisabledTest}：同一个 op 必须回
 * {@code WS_AUTH_DISABLED}，而其它 op 一切照旧。
 */
@SpringBootTest(classes = MsgWsInbandAuthTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "z-msg.enabled=true",
        "z-msg.web.trusted-header-enabled=true",
        "z-msg.realtime.ticket-secret=inband-auth-secret-0123456789abcdef",
        "z-msg.ws.inband-auth-enabled=true",
        "z-msg.ws.max-sessions-per-user=3",
        "z-msg.ws.public-topics[0]=room:lobby"
})
public class MsgWsInbandAuthTest extends WsHarness {

    /** 只有 A 是成员的房间：不在 public-topics 里，只能靠 {@link TeamMembershipPolicy} 订上 */
    private static final String TEAM = "team:alpha";

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean(name = "dataSourceMsg")
        public DataSource dataSourceMsg() {
            return h2("msg_ws_inband_auth");
        }

        /**
         * 群成员表形状的最小版：{@code team:} 前缀只放行 A。
         * 没有这条策略，"换完身份要按新身份重新裁决订阅"就没有可撤销的东西
         * ——默认策略下唯一随身份变的 topic 是 {@code user:<自己>}，而那条由注册表连带摘掉。
         */
        @Bean
        public TopicAuthorizationPolicy teamMembershipPolicy() {
            return new TeamMembershipPolicy();
        }
    }

    public static class TeamMembershipPolicy implements TopicAuthorizationPolicy {
        @Override
        public boolean supports(String topic) {
            return topic != null && topic.startsWith("team:");
        }

        @Override
        public Boolean allowSubscribe(Long userId, String topic) {
            return Boolean.valueOf(TEAM.equals(topic) && userId != null && userId.longValue() == USER_A);
        }
    }

    // ---------------- 续期：同身份 ----------------

    @Test
    public void freshTicketRenewsTheSameIdentityAndIsUsableOnlyOnce() {
        Recorder a = connect(USER_A);
        awaitReady(a);

        Map<String, Object> ack = authAndWait(a, "a1", ticket(USER_A));
        assertEquals(Boolean.FALSE, ack.get("changed"),
                "同身份续期不该报成换了人: " + ack);
        assertEquals(USER_A, ((Number) ack.get("userId")).longValue());
        assertFalse(ack.containsKey("previousUserId"), "没换身份就不该出现 previousUserId: " + ack);
        assertTrue(stringList(ack.get("topics")).contains(RealtimeTopics.user(USER_A)),
                "续期不该把自己的收件箱订阅弄掉: " + ack.get("topics"));
        assertEquals(1, registry.onlineConnections(), "续期不该多出一条连接");

        // 同一张票再来一次必须失败：与握手同一把尺（1.2.0 起走 consume）
        String once = ticket(USER_A);
        assertEquals(Boolean.FALSE, authAndWait(a, "a2", once).get("changed"),
                "这张票第一次用应当成功（下面的重放才有对照）");
        Map<String, Object> replay = authRaw(a, "a3", once);
        assertEquals(RealtimeMessage.OP_ERROR, replay.get("op"),
                "重放同一张票必须报错，实际: " + replay);
        assertEquals("WS_AUTH_FAILED", replay.get("errorCode"), "重放的错误码: " + replay);

        // 报错之后连接照旧可用：负向断言的猎物就在这条推送里
        sendInboxAndWait(a, USER_A, "INB-RENEW-1", "续期后仍然收到的红点");
    }

    // ---------------- 失败必须是无害的 ----------------

    @Test
    public void badTicketFailsWithoutDroppingTheConnectionOrMovingTheIdentity() {
        Recorder a = connect(USER_A);
        awaitReady(a);
        String cid = connectionIdOf(a);

        Map<String, Object> err = authRaw(a, "b1", tamper(ticket(USER_A)));
        assertEquals("WS_AUTH_FAILED", err.get("errorCode"), "改过签名的票应当被拒: " + err);
        assertEquals("b1", err.get("clientMsgId"), "错误帧也要原样回带 clientMsgId");

        assertTrue(a.session().isOpen(), "一次验不过的 auth 不该踢掉好连接");
        assertTrue(registry.find(cid) != null, "被拒之后注册表里就该还有这条连接");
        assertEquals(Long.valueOf(USER_A), registry.find(cid).getUserId().longValue(),
                "被拒的 auth 绝不能改身份");

        // 猎物：同一条连接此后仍能收到自己的推送
        sendInboxAndWait(a, USER_A, "INB-BAD-1", "坏票之后仍能收到");
    }

    @Test
    public void authFrameWithoutTokenIsABadFrameAndANormalOneStillWorksAfterwards() {
        Recorder a = connect(USER_A);
        awaitReady(a);

        send(a, "{\"op\":\"auth\",\"clientMsgId\":\"c1\"}");
        Map<String, Object> err = awaitErrorCode(a, "WS_BAD_FRAME");
        assertEquals("c1", err.get("clientMsgId"));

        // 对照：同一台客户端只是没带 token，带上了就该成功（不是"这条 op 根本没接"）
        assertEquals(USER_A, ((Number) authAndWait(a, "c2", ticket(USER_A)).get("userId")).longValue());
    }

    // ---------------- 换身份：最要紧的一条 ----------------

    @Test
    public void switchingIdentityStopsTheOldUsersRedDotAndStartsTheNewUsers() {
        Recorder rec = connect(USER_A);
        awaitReady(rec);
        subscribeAndWait(rec, "s1", LOBBY);

        Map<String, Object> ack = authAndWait(rec, "d1", ticket(USER_B));
        assertEquals(Boolean.TRUE, ack.get("changed"), "换了人必须报 changed=true: " + ack);
        assertEquals(USER_B, ((Number) ack.get("userId")).longValue());
        assertEquals(Long.valueOf(USER_A), ((Number) ack.get("previousUserId")).longValue());
        List<String> revoked = stringList(ack.get("revoked"));
        assertTrue(revoked.contains(RealtimeTopics.user(USER_A)),
                "旧身份的收件箱 topic 必须被撤销并如实回报，否则 A 的红点会继续投进这条自称 B 的连接: " + ack);
        List<String> topics = stringList(ack.get("topics"));
        assertTrue(topics.contains(RealtimeTopics.user(USER_B)),
                "新身份自己的收件箱要自动补上，否则前端得再发一次 subscribe 才有红点: " + topics);
        assertTrue(topics.contains(LOBBY), "与身份无关的房间订阅不该被顺手退掉: " + topics);

        // 换完之后：B 的收得到、A 的收不到。先发 A 再发 B —— 站内信的实时推送发生在
        // gateway.send() 的调用线程里，所以等到 B 那条时 A 那条的投递尝试早已结束。
        MessageSendResult toA = gateway.send(inbox(USER_A, "INB-SWITCH-A", "只属于 A 的红点"));
        MessageSendResult toB = gateway.send(inbox(USER_B, "INB-SWITCH-B", "换身份后属于 B 的红点"));
        assertTrue(toA.isSuccess() && toB.isSuccess(), "两条投递都应成功: " + toA + " / " + toB);

        Map<String, Object> gotB = awaitFrame(rec, withTitle("换身份后属于 B 的红点"), "B 的推送");
        assertEquals(RealtimeTopics.user(USER_B), gotB.get("topic"));
        assertFalse(containsTitle(rec, "只属于 A 的红点"),
                "换过身份的连接不能再收到旧用户的站内信: " + rec.dump());
    }

    @Test
    public void switchingBackRestoresTheOriginalInbox() {
        Recorder rec = connect(USER_A);
        awaitReady(rec);
        authAndWait(rec, "e1", ticket(USER_B));
        Map<String, Object> back = authAndWait(rec, "e2", ticket(USER_A));

        assertEquals(Boolean.TRUE, back.get("changed"), "从 B 换回 A 也是换身份: " + back);
        assertEquals(Long.valueOf(USER_B), ((Number) back.get("previousUserId")).longValue());
        assertTrue(stringList(back.get("topics")).contains(RealtimeTopics.user(USER_A)),
                "换回来要重新订上自己的收件箱: " + back.get("topics"));

        sendInboxAndWait(rec, USER_A, "INB-BACK", "换回原身份后的红点");
    }

    /**
     * 换身份要按**新身份**重新裁决每一条已有订阅。
     * <p>
     * 默认策略下随身份变的只有 {@code user:<自己>}（那条注册表会连带摘掉），所以这里挂一条
     * 成员表形状的策略：{@code team:alpha} 只放行 A。不这样造出猎物，"换完身份复核订阅"
     * 这句承诺在测试里就是空的——摘掉那段复核循环，其余六条用例照样全绿。
     */
    @Test
    public void subscriptionTheNewIdentityCannotHoldIsRevokedAndStopsDelivering() {
        Recorder rec = connect(USER_A);
        awaitReady(rec);
        assertVerbContains(subscribeAndWait(rec, "g1", TEAM), "subscribed", TEAM);
        // 猎物：换之前这条 topic 确实在往这条连接投帧
        assertTrue(publisher.publish(TEAM, RealtimeMessage.KIND_CHAT, one("text", "换身份前 team 里听得见")) > 0,
                "A 名下的 team 订阅没生效，下面的撤销断言就没有对照");
        awaitFrame(rec, withText("换身份前 team 里听得见"), "team 帧（换身份前的猎物）");

        Map<String, Object> ack = authAndWait(rec, "g2", ticket(USER_B));
        assertTrue(stringList(ack.get("revoked")).contains(TEAM),
                "新身份不配持有的 topic 必须撤销并如实回报，否则 B 的连接还在听 A 的团队频道: " + ack);
        assertFalse(stringList(ack.get("topics")).contains(TEAM),
                "报了 revoked 却没真退订：topics 里还留着 " + TEAM + ": " + ack);
        // 数索引而不是数客户端有没有收到：收到"没有"可能是时序，命中数为 0 才是索引真的搬走了
        assertEquals(0, registry.subscriberCount(TEAM),
                "注册表里这条连接还挂在 team 上，A 之后发的每一帧都会继续投进这条自称 B 的连接");

        // 换回 A：服务端没有"这个用户该有哪些频道"的可枚举清单（那要改 TopicAuthorizationPolicy 契约），
        // 所以自动补回来的只有身份派生的那一条收件箱，其余频道要靠客户端重新 subscribe。
        // 这里钉的是写进协议的现状：换成"会自动补"的断言等于把一个没做的功能当成已交付。
        Map<String, Object> back = authAndWait(rec, "g3", ticket(USER_A));
        assertTrue(stringList(back.get("topics")).contains(RealtimeTopics.user(USER_A)),
                "换回原身份要重新订上自己的收件箱: " + back);
        assertFalse(stringList(back.get("topics")).contains(TEAM),
                "非收件箱订阅不会自动恢复，客户端重订前连接不该悄悄挂回频道上: " + back);
        assertVerbContains(subscribeAndWait(rec, "g4", TEAM), "subscribed", TEAM);
        assertTrue(publisher.publish(TEAM, RealtimeMessage.KIND_CHAT, one("text", "换回后重订又听得见了")) > 0,
                "重订只进了 session.topics() 没进索引的话，前端会以为订上了却一帧收不到");
        awaitFrame(rec, withText("换回后重订又听得见了"), "重新订阅后的 team 帧");
    }

    /**
     * 票就是凭据：落进应用日志等于"任何能看到日志的人都能拿它冒充这个用户"。
     * <p>
     * 光断言"日志里没有 token"是可以空跑的——appender 挂错 logger、或那条日志压根没打，
     * 都表现为零命中。所以这里同时钉"捕到的确实是换票那条日志"，以及"票的签名段确实有效过"
     * （同一张票在这条用例里被服务端接受过，不是随便一串字符）。
     */
    @Test
    public void ticketNeverReachesTheLogsButTheRebindLineDoes() {
        Recorder rec = connect(USER_A);
        awaitReady(rec);
        String used;
        String rejected;
        String captured;
        try (LogCapture logs = LogCapture.start()) {
            used = ticket(USER_A);
            assertEquals(USER_A, ((Number) authAndWait(rec, "h1", used).get("userId")).longValue(),
                    "这张票必须真的被接受，否则'日志里没有它'不算数");
            rejected = tamper(ticket(USER_B));
            assertEquals("WS_AUTH_FAILED", authRaw(rec, "h2", rejected).get("errorCode"));
            captured = logs.text();
        }
        assertTrue(captured.contains("带内换票"),
                "没捕到换票日志，下面的零命中就是空跑: " + captured);
        for (String token : new String[]{used, rejected}) {
            String signature = token.substring(token.lastIndexOf('.') + 1);
            assertFalse(captured.contains(signature),
                    "日志里出现了票的签名段（拿到日志就能冒充该用户）: " + captured);
        }
    }

    // ---------------- 上限：换身份要落进新用户名下重跑挤占 ----------------

    @Test
    public void switchingIntoACrowdedUserEvictsThatUsersOldestConnection() {
        // 本类配了 max-sessions-per-user=3：B 名下先占满 3 条（还没挤），再把 A 的一条换成 B
        for (int i = 0; i < 3; i++) {
            Recorder b = connect(USER_B);
            awaitReady(b);
        }
        assertEquals(3, registry.onlineConnections(), "B 名下应当正好 3 条连接（挤占的猎物成立）");
        assertEquals(1, registry.onlineUsers());

        Recorder a = connect(USER_A);
        awaitReady(a);
        assertEquals(4, registry.onlineConnections());

        authAndWait(a, "f1", ticket(USER_B));
        assertTrue(waitUntil(() -> registry.onlineConnections() == 3, FRAME_TIMEOUT_MS),
                "换身份等于在新用户名下多了一条连接，最老那条必须被挤掉，实际在线: "
                        + registry.onlineConnections());
        assertEquals(1, registry.onlineUsers(), "换完应当只剩 B 这一个用户");
    }

    // ---------------- 工具 ----------------

    private void auth(Recorder rec, String clientMsgId, String token) {
        send(rec, "{\"op\":\"auth\",\"clientMsgId\":\"" + clientMsgId + "\",\"token\":\"" + token + "\"}");
    }

    /** 发 auth 并等它这一条的回帧（ack 或 error 都算，按 clientMsgId 对号）。 */
    private Map<String, Object> authRaw(Recorder rec, String clientMsgId, String token) {
        auth(rec, clientMsgId, token);
        assertTrue(waitUntil(() -> rec.first(f -> clientMsgId.equals(f.get("clientMsgId"))) != null,
                FRAME_TIMEOUT_MS), "没等到 clientMsgId=" + clientMsgId + " 的回帧: " + rec.dump());
        return rec.first(f -> clientMsgId.equals(f.get("clientMsgId")));
    }

    private Map<String, Object> authAndWait(Recorder rec, String clientMsgId, String token) {
        Map<String, Object> frame = authRaw(rec, clientMsgId, token);
        assertEquals(RealtimeMessage.OP_ACK, frame.get("op"), "auth 应当回 ack，实际: " + frame);
        return payloadOf(frame);
    }

    private void sendInboxAndWait(Recorder rec, long userId, String bizType, String title) {
        MessageSendResult r = gateway.send(inbox(userId, bizType, title));
        assertTrue(r.isSuccess(), "投递必须成功，否则后面的收帧断言没有猎物: " + r.getErrorCode());
        awaitFrame(rec, withTitle(title), "kind=inbox 的推送帧：" + title);
    }
}
