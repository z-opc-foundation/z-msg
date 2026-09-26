package com.zifang.z.msg.im;

import com.zifang.z.msg.api.RealtimeMessage;
import com.zifang.z.msg.api.RealtimePublisher;
import com.zifang.z.msg.api.RealtimeTopics;
import com.zifang.z.msg.core.json.MsgJson;
import com.zifang.z.msg.im.domain.entity.ImMessageDO;
import com.zifang.z.msg.im.domain.model.ImConvTypes;
import com.zifang.z.msg.ws.config.WsProperties;
import com.zifang.z.msg.ws.session.WsSessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 真起 Boot 上下文、真开两条 JSR-356 socket 的端到端用例。
 * <p>
 * 这一层不能省：{@code ImTopicAuthorizationPolicy} 只有接进 {@code TopicAuthorizer} 的链子里、
 * 并且真的被一次订阅问到，才算工作过。mock 一个 {@code RealtimePublisher} 出来只证明我会 mock。
 * <p>
 * 注：{@code z-msg-ws} 测试包里的 {@code WsHarness} 是 package-private 的
 * （在 {@code com.zifang.z.msg.ws} 包内），跨模块拿不到，所以这里的 socket 骨架是自己写的，
 * 但沿用同一套"轮询到期限"的等待纪律。
 */
public class ImRealtimeTwoSocketTest extends ImSpringTestSupport {

    private static final String LOBBY = "room:lobby";

    @Autowired
    private WsProperties wsProperties;
    @Autowired
    private WsSessionRegistry registry;
    @Autowired
    private RealtimePublisher publisher;

    private final StandardWebSocketClient wsClient = new StandardWebSocketClient();
    private final List<WebSocketSession> sockets = new CopyOnWriteArrayList<WebSocketSession>();

    @AfterEach
    public void closeSockets() {
        for (WebSocketSession s : sockets) {
            try {
                s.close(CloseStatus.NORMAL);
            } catch (Exception ignore) {
                // 已经被服务端关掉
            }
        }
        sockets.clear();
        // 上一条用例漏关连接会污染这里（挤占上限、串帧），所以等到注册表归零再进下一条。
        awaitUntilTrue(() -> registry.onlineConnections() == 0,
                "用例结束后注册表应当空掉，实际残留 " + registry.onlineIds());
    }

    // ---------------------------------------------------------------- 金路：成员收得到

    @Test
    public void memberSocketOnRoomTopicReallyReceivesWhatAnotherMemberSent() {
        Long conv = conversationService.createGroup(A, Arrays.asList(B, OUTSIDER),
                "实时下发", null, null, ImConvTypes.GROUP).getId();
        String topic = RealtimeTopics.room(conv);

        Recorder b = connect(B);
        awaitReady(b);
        assertVerbContains(subscribeAndWait(b, "sub-b", topic), "subscribed", topic);

        // 发一条：帧必须落在 room:<id> 上，seq 与库里那条消息一致
        ImMessageHolder sent = ImMessageHolder.of(messageService.send(conv, A, "A 发给全群的正文"));
        Map<String, Object> frame = awaitChatOn(b, topic, "A 发给全群的正文", "B 订了 room 就该收到");
        assertEquals(topic, frame.get("topic"), "会话消息要投在 room: 上，不能投到个人 topic");
        assertEquals(RealtimeMessage.OP_MESSAGE, frame.get("op"));
        assertEquals(RealtimeMessage.KIND_CHAT, frame.get("kind"));
        assertEquals(sent.seq, longOf(frame.get("seq")),
                "帧外层的 seq 必须等于消息的 seq —— 客户端按它做增量补拉");
        assertNotNull(frame.get("ts"));
        Map<String, Object> payload = payloadOf(frame);
        assertEquals(conv, idOf(payload.get("conversationId")));
        assertEquals(A, idOf(payload.get("senderUserId")));
        assertEquals(sent.id, idOf(payload.get("id")));
        assertEquals("TEXT", payload.get("msgType"));
        assertNotNull(payload.get("createdTime"), "时间要带");
        assertTrue(payload.get("createdTime") instanceof String,
                "createdTime 要和 REST 那一行、站内信帧同形（ISO 字符串），不能是 epoch 数字: "
                        + payload.get("createdTime"));
        ImMessageDO stored = messageService.history(conv, A, 0L, 10).get(0);
        assertEquals(sent.seq, stored.getSeq().longValue(), "补拉回来的就该是刚发的那一条");
        assertEquals(stored.getCreatedTime().truncatedTo(ChronoUnit.SECONDS),
                LocalDateTime.parse((String) payload.get("createdTime")),
                "帧里的时间必须等于库里那一行（截到秒）：MySQL 的 created_time 只存整秒，"
                        + "不截的话同一消息在帧里和在历史里对不上，前端就得为同一个字段准备两套解析");

        // 订阅者自己也收得到自己发的（多端一致：同一账号的另一台设备要看到刚敲出去的字）
        Recorder a = connect(A);
        awaitReady(a);
        assertVerbContains(subscribeAndWait(a, "sub-a", topic), "subscribed", topic);
        messageService.send(conv, A, "A 自己发的第二条");
        awaitChatOn(a, topic, "A 自己发的第二条", "A 的 room 帧");
        awaitChatOn(b, topic, "A 自己发的第二条", "B 也在同一个 room 里");
    }

    // ---------------------------------------------------------------- 非成员订不到（带正向对照）

    @Test
    public void strangerSocketCannotSubscribeTheRoomWhileAMemberSubscribeOfTheSameTopicWorks() {
        Long conv = conversationService.createGroup(A, Collections.singletonList(B),
                "旁听防护", null, null, ImConvTypes.GROUP).getId();
        String topic = RealtimeTopics.room(conv);

        Recorder stranger = connect(OUTSIDER);
        awaitReady(stranger);
        Recorder member = connect(B);
        awaitReady(member);

        // 负：非成员订阅 -> WS_TOPIC_FORBIDDEN，并且要把 clientMsgId 带回去（否则客户端气泡卡在"订阅中"）
        subscribe(stranger, "sub-deny", topic);
        Map<String, Object> err = awaitFrame(stranger,
                f -> RealtimeMessage.OP_ERROR.equals(f.get("op")) && "sub-deny".equals(f.get("clientMsgId")),
                "非成员订阅被拒");
        assertEquals("WS_TOPIC_FORBIDDEN", err.get("errorCode"), "实际: " + err);
        assertTrue(String.valueOf(err.get("errorMessage")).contains(topic),
                "报错要点名是哪个 topic: " + err);

        // 正（同一 topic、同一时刻）：成员的订阅必须成功 —— 证明上面那句拒的是成员判定，
        // 不是"这个 room 压根不存在"或"订阅功能没实现"
        assertVerbContains(subscribeAndWait(member, "sub-ok", topic), "subscribed", topic);

        // 猎物进笼：会话里发一条，成员收到，非成员一条都没有
        messageService.send(conv, A, "只有成员能听到的内容");
        awaitChatOn(member, topic, "只有成员能听到的内容", "成员的 room 帧");
        assertFalse(containsChatWithText(stranger, "只有成员能听到的内容"),
                "被拒的订阅不能还在收帧: " + stranger.dump());
        assertFalse(stranger.topicsSubscribed().contains(topic),
                "被拒之后这个连接的订阅集里不该出现该 room: " + stranger.topicsSubscribed());

        // 非成员连 room: 都拿不到，但换成本模块不管辖的公共 room 就要放行 ——
        // 这条钉的是三态里"不认识就返回 null"：如果 im 的策略对未知会话返回 FALSE，
        // 宿主在 z-msg.ws.public-topics 里配的 room:lobby 就会被整条链路否决。
        assertVerbContains(subscribeAndWait(stranger, "sub-lobby", LOBBY), "subscribed", LOBBY);
        // 命中数是 2 而不是 1：ws 握手时就把每条连接自动订上了 z-msg.ws.public-topics
        // （见 MsgWebSocketHandler.afterConnectionEstablished），所以这一刻两条连接都在 lobby 里。
        assertEquals(2, publisher.publish(LOBBY, RealtimeMessage.KIND_CHAT,
                Collections.singletonMap("text", "公共房间还在")),
                "两条连接都被自动订上了公共 room，命中数必须是 2");
        // 对照：没人订的 topic 命中 0 —— 上面那个 2 是真订阅数，不是"逢发必算所有连接"
        assertEquals(0, publisher.publish(RealtimeTopics.room(987654321987L), RealtimeMessage.KIND_CHAT,
                Collections.singletonMap("text", "没人订过这条")), "对照组：无订阅者必须 0 命中");
        awaitFrame(stranger, f -> "公共房间还在".equals(textOf(payloadOf(f))), "公共 room 的帧");
        awaitFrame(member, f -> "公共房间还在".equals(textOf(payloadOf(f))),
                "成员那条连接也该收到（它只是没主动订 lobby，握手时就订上了）");
    }

    // ---------------------------------------------------------------- 客户端代发被拒，服务端链路是活的

    @Test
    public void clientPublishIntoRoomIsDeniedEvenForMemberWhileServerSideSendReachesTheRoom() {
        Long conv = conversationService.createGroup(A, Collections.singletonList(B),
                "代发防护", null, null, ImConvTypes.GROUP).getId();
        String topic = RealtimeTopics.room(conv);
        Recorder a = connect(A);
        awaitReady(a);
        assertVerbContains(subscribeAndWait(a, "sub-p", topic), "subscribed", topic);

        send(a, "{\"op\":\"publish\",\"clientMsgId\":\"p1\",\"topic\":\"" + topic
                + "\",\"kind\":\"chat\",\"payload\":{\"content\":\"从客户端塞进来的假消息\"}}");
        Map<String, Object> err = awaitFrame(a, f -> "p1".equals(f.get("clientMsgId")), "op=publish 的拒绝");
        assertEquals(RealtimeMessage.OP_ERROR, err.get("op"), "实际: " + err);
        assertEquals("WS_TOPIC_FORBIDDEN", err.get("errorCode"),
                "放开 op=publish 等于允许往房间里塞不落库的假消息: " + err);

        // 对照：成员身份在"订阅"这一面是有效的（同一条 topic 刚刚还被订阅成功），
        // 而且服务端这条路真的能进这个 room —— 上面的拒来自 publish 这一面，不是这条连接坏了
        messageService.send(conv, A, "服务端落库之后发的帧");
        Map<String, Object> frame = awaitChatOn(a, topic, "服务端落库之后发的帧", "对照：服务端侧投递是活的");
        assertEquals(1L, longOf(frame.get("seq")));
        assertEquals(1, messageService.history(conv, A, 0L, 10).size(), "被拒的那条假消息不该在库里");
    }

    // ---------------------------------------------------------------- user: 侧投递

    @Test
    public void singleChatAlsoLandsOnThePeersOwnUserTopicWithoutSubscribing() {
        Long conv = conversationService.single(A, B, null).getId();
        Recorder b = connect(B);
        awaitReady(b);
        // 关键前置：B 一条 subscribe 都没发，只带着握手时自动订阅的 user:<id>
        messageService.send(conv, A, "轻客户端也能收到的 DM");
        Map<String, Object> frame = awaitChatOn(b, RealtimeTopics.user(B), "轻客户端也能收到的 DM",
                "user: 侧的 DM 帧");
        assertEquals(RealtimeTopics.user(B), frame.get("topic"),
                "没订房间的连接靠自己的 user: topic 也要能收到单聊");
        assertEquals(conv, idOf(payloadOf(frame).get("conversationId")));

        // 对照一：同一条消息在 room: 上没有别的订阅者，不影响上面这条投递
        // 对照二：另一个人的连接收不到——离线/旁人都不该被这条帧打扰
        Recorder other = connect(OUTSIDER);
        awaitReady(other);
        messageService.send(conv, A, "只属于 A 与 B 的一条");
        awaitChatOn(b, RealtimeTopics.user(B), "只属于 A 与 B 的一条", "B 的第二条 DM 帧");
        assertFalse(containsChatWithText(other, "只属于 A 与 B 的一条"),
                "别人的单聊不能扇到无关连接上: " + other.dump());
    }

    @Test
    public void groupUnderTheFanOutLimitPushesUserTopicsAndChatRoomDoesNot() {
        // GROUP：成员少，按 z-msg.im.user-side-push 扇到每个人自己的 topic
        Long group = conversationService.createGroup(A, Arrays.asList(B, OUTSIDER),
                "小群", null, null, ImConvTypes.GROUP).getId();
        Recorder outsider = connect(OUTSIDER);
        awaitReady(outsider);
        messageService.send(group, A, "小群里的一条");
        awaitChatOn(outsider, RealtimeTopics.user(OUTSIDER), "小群里的一条", "GROUP 的 user: 侧投递");

        // 对照：ROOM 不扇（大房间里一条消息打 N 次投递是纯粹的自伤，只走 room:）
        Long room = conversationService.createGroup(A, Collections.singletonList(OUTSIDER),
                "聊天室", null, null, ImConvTypes.ROOM).getId();
        messageService.send(room, A, "聊天室里的一条");
        // 先等一条会到的（同一条连接的对照），再断言不会到的那条：
        // 否则"没收到"可能只是"还没发"
        messageService.send(group, A, "小群里又一条");
        awaitChatOn(outsider, RealtimeTopics.user(OUTSIDER), "小群里又一条", "同一条连接仍然收得到 GROUP 的帧");
        assertFalse(containsChatWithText(outsider, "聊天室里的一条"),
                "ROOM 不该往 user: 扇: " + outsider.dump());
        // 对照二：ROOM 的 room: 侧是真能到的（不扇 user: 是选择，不是链路坏了）
        assertVerbContains(subscribeAndWait(outsider, "sub-room", RealtimeTopics.room(room)),
                "subscribed", RealtimeTopics.room(room));
        messageService.send(room, A, "聊天室里订了房间的人能听到");
        awaitChatOn(outsider, RealtimeTopics.room(room), "聊天室里订了房间的人能听到", "ROOM 的 room: 帧");
    }

    // ---------------------------------------------------------------- 已读帧

    @Test
    public void markReadEmitsAReadFrameIntoTheRoomAndOnlyIntoIt() {
        Long conv = conversationService.createGroup(A, Arrays.asList(B, OUTSIDER),
                "已读帧", null, null, ImConvTypes.GROUP).getId();
        String topic = RealtimeTopics.room(conv);
        messageService.send(conv, A, "给你读");
        Recorder a = connect(A);
        awaitReady(a);
        assertVerbContains(subscribeAndWait(a, "sub-r", topic), "subscribed", topic);
        Recorder stranger = connect(OUTSIDER_2);
        awaitReady(stranger);

        readService.markRead(conv, B, 1L);
        Map<String, Object> frame = awaitFrame(a,
                f -> RealtimeMessage.KIND_READ.equals(f.get("kind")), "kind=read 的帧");
        assertEquals(topic, frame.get("topic"), "已读回执只进会话，不往个人 topic 上打");
        Map<String, Object> payload = payloadOf(frame);
        assertEquals(B, idOf(payload.get("userId")), "帧里要点明是谁读到的");
        assertEquals(1L, longOf(payload.get("lastReadSeq")));

        // 对照 + 负：非成员没订到这条 room，所以这条 read 也与他无关
        subscribe(stranger, "sub-r-denied", topic);
        awaitFrame(stranger, f -> "sub-r-denied".equals(f.get("clientMsgId"))
                && RealtimeMessage.OP_ERROR.equals(f.get("op")), "非成员订阅被拒");
        readService.markRead(conv, B, 1L);
        assertTrue(waitUntil(() -> countKind(a, RealtimeMessage.KIND_READ) >= 2),
                "重复标已读也要再发一帧（展示态刷新），这一条同时证明上面的收到不是巧合");
        assertFalse(containsKind(stranger, RealtimeMessage.KIND_READ),
                "被拒的连接不该看到别人的已读状态: " + stranger.dump());
    }

    // ---------------------------------------------------------------- socket 骨架

    private String ticket(long userId) {
        ResponseEntity<String> resp = get("/api/msg/inbox/ws-token", Long.valueOf(userId));
        assertOk(resp, "换 ticket");
        Object token = dataMap(resp).get("token");
        assertNotNull(token, "响应里没有 token: " + resp.getBody());
        return String.valueOf(token);
    }

    private Recorder connect(long userId) {
        String url = "ws://127.0.0.1:" + port + wsProperties.getPath() + "?token=" + ticket(userId);
        Recorder rec = new Recorder();
        try {
            WebSocketSession s = wsClient.doHandshake(rec, url).get(WAIT_TIMEOUT_MS,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            sockets.add(s);
            rec.attach(s);
            assertTrue(s.isOpen(), "握手成功但不是打开状态: " + url);
            return rec;
        } catch (Exception e) {
            throw new AssertionError("握手失败: " + url + " -> " + e, e);
        }
    }

    private static void send(Recorder rec, String frame) {
        try {
            rec.session().sendMessage(new TextMessage(frame));
        } catch (Exception e) {
            throw new AssertionError("发帧失败: " + frame, e);
        }
    }

    private static void subscribe(Recorder rec, String clientMsgId, String... topics) {
        StringBuilder sb = new StringBuilder("{\"op\":\"subscribe\",\"clientMsgId\":\"")
                .append(clientMsgId).append("\",\"topics\":[");
        for (int i = 0; i < topics.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(topics[i]).append('"');
        }
        send(rec, sb.append("]}").toString());
    }

    private static Map<String, Object> subscribeAndWait(Recorder rec, String clientMsgId, String... topics) {
        subscribe(rec, clientMsgId, topics);
        return awaitFrame(rec, f -> RealtimeMessage.OP_ACK.equals(f.get("op"))
                && clientMsgId.equals(f.get("clientMsgId")), "ack(" + clientMsgId + ")");
    }

    private static void awaitReady(Recorder rec) {
        awaitFrame(rec, f -> RealtimeMessage.OP_READY.equals(f.get("op")), "ready 帧");
    }

    /** 等到一条落在 room/user 上的 chat 帧且正文匹配。 */
    private static Map<String, Object> awaitChat(Recorder rec, String content, String what) {
        return awaitFrame(rec, f -> isChatWithContent(f, content), what);
    }

    /**
     * 同上，但把 topic 也钉住：一条消息同时投了 {@code room:} 与 {@code user:}，
     * 只按正文匹配的话"落在哪条 topic 上"就取决于哪帧先到，断言会漂。
     */
    private static Map<String, Object> awaitChatOn(Recorder rec, String topic, String content, String what) {
        return awaitFrame(rec, f -> topic.equals(f.get("topic")) && isChatWithContent(f, content), what);
    }

    private static boolean isChatWithContent(Map<String, Object> frame, String content) {
        if (!RealtimeMessage.OP_MESSAGE.equals(frame.get("op"))
                || !RealtimeMessage.KIND_CHAT.equals(frame.get("kind"))) {
            return false;
        }
        return content.equals(payloadOf(frame).get("content"));
    }

    private static boolean containsChatWithText(Recorder rec, String content) {
        for (Map<String, Object> f : rec.frames()) {
            if (isChatWithContent(f, content)) {
                return true;
            }
        }
        return false;
    }

    private static int countKind(Recorder rec, String kind) {
        int n = 0;
        for (Map<String, Object> f : rec.frames()) {
            if (kind.equals(f.get("kind"))) {
                n++;
            }
        }
        return n;
    }

    private static boolean containsKind(Recorder rec, String kind) {
        return countKind(rec, kind) > 0;
    }

    private static Map<String, Object> awaitFrame(Recorder rec, Predicate<Map<String, Object>> p, String what) {
        final AtomicReference<Map<String, Object>> hit = new AtomicReference<Map<String, Object>>();
        boolean got = waitUntil(() -> {
            for (Map<String, Object> f : rec.frames()) {
                if (p.test(f)) {
                    hit.set(f);
                    return true;
                }
            }
            return false;
        });
        if (!got) {
            fail("超时未等到" + what + "。已收到: " + rec.dump());
        }
        return hit.get();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadOf(Map<String, Object> frame) {
        Object raw = frame.get("payload");
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        if (raw instanceof String) {
            Map<String, Object> m = MsgJson.toMap((String) raw);
            return m == null ? new HashMap<String, Object>() : m;
        }
        return new HashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    private static Object textOf(Map<String, Object> payload) {
        Object t = payload.get("text");
        return t == null ? null : String.valueOf(t);
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object raw) {
        List<String> out = new ArrayList<String>();
        if (raw instanceof List) {
            for (Object o : (List<Object>) raw) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    private static void assertVerbContains(Map<String, Object> ack, String verb, String topic) {
        assertTrue(stringList(payloadOf(ack).get(verb)).contains(topic),
                verb + " 应当包含 " + topic + ": " + ack);
    }

    /** 一条消息的 id + seq，断言时少写几行。 */
    private static final class ImMessageHolder {
        private final long id;
        private final long seq;

        private ImMessageHolder(long id, long seq) {
            this.id = id;
            this.seq = seq;
        }

        static ImMessageHolder of(com.zifang.z.msg.im.domain.entity.ImMessageDO m) {
            return new ImMessageHolder(m.getId().longValue(), m.getSeq().longValue());
        }
    }

    /** 客户端侧的帧记录器。 */
    static final class Recorder extends TextWebSocketHandler {
        private final List<Map<String, Object>> frames =
                Collections.synchronizedList(new ArrayList<Map<String, Object>>());
        private final List<String> topics = new CopyOnWriteArrayList<String>();
        private volatile WebSocketSession session;

        void attach(WebSocketSession s) {
            this.session = s;
        }

        WebSocketSession session() {
            return session;
        }

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage message) {
            Map<String, Object> frame = MsgJson.toMap(message.getPayload());
            if (frame == null) {
                frame = new HashMap<String, Object>();
                frame.put("__not_json__", message.getPayload());
            }
            frames.add(frame);
            rememberTopicChanges(frame);
        }

        /** 自己维护一份"我以为我订上了什么"，用来证被拒的订阅没挂上 topic。 */
        private void rememberTopicChanges(Map<String, Object> frame) {
            Map<String, Object> payload = payloadOf(frame);
            String op = String.valueOf(frame.get("op"));
            if (RealtimeMessage.OP_READY.equals(op)) {
                topics.clear();
                topics.addAll(stringList(payload.get("topics")));
                return;
            }
            if (RealtimeMessage.OP_ACK.equals(op)) {
                topics.clear();
                topics.addAll(stringList(payload.get("topics")));
            }
        }

        List<String> topicsSubscribed() {
            return new ArrayList<String>(topics);
        }

        List<Map<String, Object>> frames() {
            return new ArrayList<Map<String, Object>>(frames);
        }

        String dump() {
            return MsgJson.toJson(frames());
        }
    }
}
