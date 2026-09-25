package com.zifang.z.msg.ws;

import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.api.RealtimeMessage;
import com.zifang.z.msg.api.RealtimeTopics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * z-msg-ws 的端到端用例：真 Tomcat + 真 H2 + 真 JSR-356 客户端，全程不 mock 传输层。
 * <p>
 * 覆盖的是宿主真实会走的那条路：只把 jar 放进 classpath、只配一把 ticket 密钥，
 * 自动配置有没有挂上、端点能不能握手、帧能不能双向走、越权面是否真关住。
 * <p>
 * 每一条"应当被拒"的断言都在同一条用例里配了"换个合法入参就要成功"的对照——
 * 否则端点没注册、publish 是空实现，也能让整类绿。
 */
@SpringBootTest(classes = MsgWsEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "z-msg.enabled=true",
        "z-msg.web.trusted-header-enabled=true",
        "z-msg.realtime.ticket-secret=e2e-ws-secret-0123456789abcdef",
        "z-msg.ws.public-topics[0]=room:lobby",
        "z-msg.ws.max-sessions-per-user=2"
})
public class MsgWsEndToEndTest extends WsHarness {

    /** 没有任何连接订阅过的房间：用来证"命中数是数出来的，不是在线连接数" */
    private static final String NOBODY_HOME = "room:9101";

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {
        /**
         * 顶掉 core 里那台 MySQL/Druid 的 dataSourceMsg。表结构直接执行 core 打包的那份
         * {@code z-msg/sql/schema-h2.sql}，所以这里验的 DDL 与 SchemaParityTest 守护的是同一份。
         */
        @Bean(name = "dataSourceMsg")
        public DataSource dataSourceMsg() {
            return h2("msg_ws_e2e");
        }
    }

    // ---------------- 金路：HTTP 换 ticket -> 握手 -> 站内信实时进框 ----------------

    @Test
    public void ticketFromHttpOpensSocketThatReceivesItsOwnInboxPush() {
        Recorder rec = connect(USER_A);

        Map<String, Object> ready = awaitFrame(rec, opIs(RealtimeMessage.OP_READY), "ready 帧");
        Map<String, Object> info = payloadOf(ready);
        assertEquals(Long.valueOf(USER_A), ((Number) info.get("userId")).longValue(),
                "握手方只能拿到自己的身份，不接受帧里自称的 userId");
        assertNotNull(info.get("connectionId"));
        List<String> topics = stringList(info.get("topics"));
        assertTrue(topics.contains(RealtimeTopics.user(USER_A)),
                "自己的收件箱 topic 必须自动订阅，否则前端要先发一次 subscribe 才收得到红点: " + topics);
        assertTrue(topics.contains(LOBBY), "配置的 public-topics 应当自动订阅: " + topics);

        MessageSendResult sent = gateway.send(inbox(USER_A, "WS-INBOX-1", "A 的实时红点"));
        assertTrue(sent.isSuccess(), "IN_APP 必须投递成功，否则下面的收帧断言没有猎物: "
                + sent.getErrorCode() + " / " + sent.getErrorMessage());

        Map<String, Object> push = awaitFrame(rec,
                f -> RealtimeMessage.KIND_INBOX.equals(f.get("kind")), "kind=inbox 的推送帧");
        assertEquals(RealtimeMessage.OP_MESSAGE, push.get("op"));
        assertEquals(RealtimeTopics.user(USER_A), push.get("topic"),
                "站内信帧要落在自己的 user topic 上，实际: " + push.get("topic"));
        Map<String, Object> body = payloadOf(push);
        assertEquals("A 的实时红点", body.get("title"));
        assertEquals(Boolean.TRUE, body.get("unread"), "新推送要带未读语义，前端据此点红点");
        assertNotNull(body.get("id"), "payload 要带站内信主键，前端标已读时要用它: " + body);
        assertTrue(((Number) push.get("seq")).longValue() > 0L,
                "帧必须带 topic 内 seq，否则客户端无法做增量补拉: " + push.get("seq"));
    }

    // ---------------- 隔离 ----------------

    @Test
    public void anotherUsersInboxNeverReachesThisSocket() {
        Recorder a = connect(USER_A);
        Recorder b = connect(USER_B);
        awaitReady(a);
        awaitReady(b);

        gateway.send(inbox(USER_A, "WS-PRIV-A", "只有 A 能看到的实时消息"));
        gateway.send(inbox(USER_B, "WS-PRIV-B", "B 自己的实时消息"));
        // 先等 B 收到自己那条：这既证明 B 的连接活着，也证明 publish 已同步走完一遍。
        // 站内信的实时推送发生在 gateway.send() 的调用线程里，所以此刻 A 那条的投递尝试
        // 早已结束，下面那句"没收到"不是时序侥幸。
        awaitFrame(b, withTitle("B 自己的实时消息"), "B 自己的推送（负向断言的猎物）");
        awaitFrame(a, withTitle("只有 A 能看到的实时消息"), "A 自己的推送（正向对照）");

        assertFalse(containsTitle(b, "只有 A 能看到的实时消息"),
                "B 的连接上不该出现 A 的站内信，实际帧: " + b.dump());
        assertFalse(containsTitle(a, "B 自己的实时消息"),
                "反向同理：A 也拿不到 B 的站内信，实际帧: " + a.dump());
    }

    // ---------------- 握手鉴权 ----------------

    @Test
    public void handshakeWithoutAValidTicketIsRefusedButAValidOneSucceeds() throws Exception {
        String base = wsUrl();
        assertHandshakeRefused(base, "完全不带 token");
        assertHandshakeRefused(base + "?userId=" + USER_A, "自称 userId 而不是 ticket");
        assertHandshakeRefused(base + "?token=" + tamper(ticket(USER_A)), "改过签名的 ticket");

        // 状态码层面把两支区分开：缺 token 与签名不对都是 401，而不是笼统的 503（那是"没配密钥"）
        assertEquals(401, handshakeStatus(port, wsProperties.getPath(), true),
                "配了密钥、缺 token 的握手应当 401");
        assertEquals(401, handshakeStatus(port, wsProperties.getPath() + "?token=bad.sig", true),
                "签名对不上同样 401");

        // 同一台服务器、同一个路径，带真 ticket 就要连上——证明上面几条来自鉴权而不是端点没注册
        awaitReady(connect(USER_A));
        assertEquals(1, registry.onlineConnections(), "只有那条合法连接该活着");
    }

    @Test
    public void repeatedTokenQueryParamIsNotAmbiguouslyAccepted() {
        String url = wsUrl() + "?token=" + ticket(USER_A) + "&token=" + ticket(USER_B);
        assertHandshakeRefused(url, "同一个 key 出现两次：不能随便挑一个当身份");
        awaitReady(connect(USER_A));
    }

    // ---------------- 订阅授权与订阅集的实际效力 ----------------

    @Test
    public void foreignTopicSubscriptionIsVetoedWhileSubscriptionsReallyGateDelivery() {
        Recorder a = connect(USER_A);
        Recorder b = connect(USER_B);
        awaitReady(a);
        awaitReady(b);

        subscribe(a, "s-forbidden", RealtimeTopics.user(USER_B), "tenant:acme");
        Map<String, Object> err = awaitErrorCode(a, "WS_TOPIC_FORBIDDEN");
        assertEquals(RealtimeMessage.OP_ERROR, err.get("op"));
        assertEquals("s-forbidden", err.get("clientMsgId"),
                "拒绝也要带回 clientMsgId，否则客户端的气泡一直卡在发送中");
        assertTrue(String.valueOf(err.get("errorMessage")).contains(RealtimeTopics.user(USER_B)),
                "报错要点名是哪个 topic 被拒: " + err);

        // 对照：退订自己的公共 topic 要成功，而且退完就真的收不到——证明订阅集是投递的闸门
        Map<String, Object> out = unsubscribeAndWait(a, "u-lobby", LOBBY);
        assertVerbContains(out, "unsubscribed", LOBBY);
        List<String> left = stringList(payloadOf(out).get("topics"));
        assertFalse(left.contains(LOBBY), "ack 里还留着刚退掉的 topic: " + left);
        assertTrue(left.contains(RealtimeTopics.user(USER_A)),
                "退订不能把自动绑上的收件箱 topic 一起清掉: " + left);

        assertEquals(1, publisher.publish(LOBBY, RealtimeMessage.KIND_CHAT, one("text", "退订之后")),
                "只剩 B 订阅着 lobby，命中数必须是 1");
        awaitFrame(b, withText("退订之后"), "B 的 lobby 帧");
        assertFalse(containsText(a, "退订之后"), "A 已退订，不该收到: " + a.dump());

        // 再对照：重新订阅同一条 topic 就又收得到
        assertVerbContains(subscribeAndWait(a, "s-lobby", LOBBY), "subscribed", LOBBY);
        assertEquals(2, publisher.publish(LOBBY, RealtimeMessage.KIND_CHAT, one("text", "重订之后")),
                "两条连接都订阅着，命中数应为 2");
        awaitFrame(a, withText("重订之后"), "A 重订之后的 lobby 帧");
    }

    // ---------------- 客户端代发：默认拒，但服务端侧链路是活的 ----------------

    @Test
    public void clientPublishIsDeniedByDefaultWhileServerPublishReachesSubscribers() {
        Recorder a = connect(USER_A);
        Recorder b = connect(USER_B);
        awaitReady(a);
        awaitReady(b);

        send(a, "{\"op\":\"publish\",\"clientMsgId\":\"p1\",\"topic\":\"" + LOBBY
                + "\",\"kind\":\"chat\",\"payload\":{\"text\":\"从客户端塞进来的帧\"}}");
        Map<String, Object> err = awaitFrame(a,
                f -> "p1".equals(f.get("clientMsgId")), "op=publish 的拒绝");
        assertEquals(RealtimeMessage.OP_ERROR, err.get("op"));
        assertEquals("WS_TOPIC_FORBIDDEN", err.get("errorCode"),
                "没有模块注册策略时，任意连接都能往群聊里写帧就是越权面，实际: " + err);

        // 对照一：服务端自己 publish 同一条 topic 要真的送到两条订阅连接——证明上面被拒来自授权层
        assertEquals(2, publisher.publish(LOBBY, RealtimeMessage.KIND_CHAT, one("text", "服务端发的")),
                "a、b 都自动订阅着 lobby");
        awaitFrame(a, withText("服务端发的"), "A 的服务端代发帧");
        awaitFrame(b, withText("服务端发的"), "B 的服务端代发帧");
        // 对照二：没人订阅的房间命中 0，证明上面那个 2 是数出来的而不是"在线连接数"
        assertEquals(0, publisher.publish(NOBODY_HOME, RealtimeMessage.KIND_CHAT, one("text", "没人听")));
        assertFalse(containsText(a, "没人听"), "没订阅的房间不该送到任意连接: " + a.dump());
    }

    // ---------------- 控制帧 ----------------

    @Test
    public void pingIsAnsweredWithClientMsgIdAndUnknownOpIsReported() {
        Recorder a = connect(USER_A);
        awaitReady(a);

        send(a, "{\"op\":\"ping\",\"clientMsgId\":\"pg-1\"}");
        assertNotNull(awaitFrame(a, f -> RealtimeMessage.OP_PONG.equals(f.get("op"))
                && "pg-1".equals(f.get("clientMsgId")), "pong"));

        send(a, "{\"op\":\"teleport\",\"clientMsgId\":\"no-1\"}");
        Map<String, Object> err = awaitErrorCode(a, "WS_OP_UNSUPPORTED");
        assertTrue(String.valueOf(err.get("errorMessage")).contains("teleport"),
                "错误信息里要点明是哪个 op: " + err);

        // 对照：一帧报错不能把整条连接打死
        send(a, "{\"op\":\"ping\",\"clientMsgId\":\"pg-2\"}");
        assertNotNull(awaitFrame(a, f -> RealtimeMessage.OP_PONG.equals(f.get("op"))
                && "pg-2".equals(f.get("clientMsgId")), "报错之后的 pong"));
    }

    @Test
    public void malformedFramesAreRejectedWithoutKillingTheConnection() {
        Recorder a = connect(USER_A);
        awaitReady(a);

        send(a, "这不是 JSON");
        assertEquals("WS_BAD_FRAME",
                awaitErrorCode(a, "WS_BAD_FRAME").get("errorCode"));

        send(a, "{\"op\":\"subscribe\",\"clientMsgId\":\"empty\",\"topics\":[]}");
        assertNotNull(awaitFrame(a, f -> "empty".equals(f.get("clientMsgId"))
                && RealtimeMessage.OP_ERROR.equals(f.get("op")), "空 topics 要报错而不是静默成功"));

        send(a, "{\"op\":\"ping\",\"clientMsgId\":\"after-bad\"}");
        assertNotNull(awaitFrame(a, f -> "after-bad".equals(f.get("clientMsgId"))
                && RealtimeMessage.OP_PONG.equals(f.get("op")), "坏帧之后连接必须仍然可用"));
    }

    // ---------------- 多设备挤占 ----------------

    @Test
    public void moreSocketsThanMaxSessionsPerUserEvictsTheOldest() throws Exception {
        assertEquals(2, wsProperties.getMaxSessionsPerUser(), "前置：上限按 2 配置");
        Recorder first = connect(USER_A);
        awaitReady(first);
        Recorder second = connect(USER_A);
        awaitReady(second);
        assertEquals(2, registry.onlineConnections(), "还没超上限时两条都该在");

        String oldestId = connectionIdOf(first);
        java.util.concurrent.CountDownLatch closed = first.closed();
        Recorder third = connect(USER_A);
        awaitReady(third);

        assertTrue(closed.await(FRAME_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS),
                "第三条连上后最早那条应被服务端关掉（z-msg.ws.max-sessions-per-user=2）");
        assertEquals(2, registry.onlineConnections(), "挤占之后注册表里只留最新两条");
        assertNull(registry.find(oldestId), "被挤掉的连接必须从注册表摘干净，否则它还在收帧");
        assertFalse(registry.onlineIds().contains(oldestId), "实际在线: " + registry.onlineIds());

        // 对照：活下来的两条照常收得到，挤占只关最老的那一条
        gateway.send(inbox(USER_A, "WS-EVICT-ALIVE", "挤占之后仍能收到的消息"));
        awaitFrame(third, withTitle("挤占之后仍能收到的消息"), "最新连接的推送");
        awaitFrame(second, withTitle("挤占之后仍能收到的消息"), "第二条的推送");
    }

    // ---------------- 未配置密钥时的 fail-closed ----------------

    @Test
    public void tokenEndpointAnswersTheTruthAboutItsOwnSecret() {
        // 这个上下文里配了密钥，所以应当签发成功；反向（没配密钥 -> 503）由
        // MsgWsFailClosedTest 用另一套配置钉住，因为这里改不了已启动的上下文。
        Map<String, Object> data = dataOf(exchange("/api/msg/inbox/ws-token", HttpMethod.GET, USER_A));
        assertNotNull(data.get("token"));
        assertTrue(((Number) data.get("expiresInSeconds")).intValue() > 0,
                "要如实告诉客户端 ticket 多久过期: " + data);
    }
}
