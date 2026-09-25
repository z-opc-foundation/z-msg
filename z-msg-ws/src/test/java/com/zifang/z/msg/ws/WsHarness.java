package com.zifang.z.msg.ws;

import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageGateway;
import com.zifang.z.msg.api.RealtimeMessage;
import com.zifang.z.msg.api.RealtimePublisher;
import com.zifang.z.msg.core.json.MsgJson;
import com.zifang.z.msg.ws.config.WsProperties;
import com.zifang.z.msg.ws.session.WsSessionRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WebSocket 端到端的公共骨架：真 H2、真容器、真 JSR-356 客户端，加一套"等到为止"的取帧断言。
 * <p>
 * 之所以把等待做成轮询谓词而不是 {@code sleep(固定值)}：帧是服务端线程发的，任何固定等待
 * 在负载高的机器上都会假红；反过来"先等再断言"才能让"没收到"这种负向断言站得住。
 */
abstract class WsHarness {

    static final long USER_A = 7101L;
    static final long USER_B = 7102L;
    static final String LOBBY = "room:lobby";
    static final long FRAME_TIMEOUT_MS = 10_000L;
    private static final long REFUSE_TIMEOUT_MS = 5_000L;

    @Autowired
    protected TestRestTemplate rest;
    @Autowired
    protected MessageGateway gateway;
    @Autowired
    protected RealtimePublisher publisher;
    @Autowired
    protected WsSessionRegistry registry;
    @Autowired
    protected WsProperties wsProperties;
    @Value("${local.server.port}")
    protected int port;
    @javax.annotation.Resource(name = "dataSourceMsg")
    protected DataSource dataSourceMsg;

    private final StandardWebSocketClient wsClient = new StandardWebSocketClient();
    private final List<WebSocketSession> sockets = new CopyOnWriteArrayList<WebSocketSession>();

    /**
     * 每台测试 JVM 里的 in-memory 库名要分开：{@code DB_CLOSE_DELAY=-1} 会让库在第一个
     * Spring 上下文之后仍然活着，第二个上下文的 {@code INIT=RUNSCRIPT} 会再执行一遍。
     */
    protected static DataSource h2(String dbName) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
                + ";INIT=RUNSCRIPT FROM 'classpath:z-msg/sql/schema-h2.sql'");
        ds.setUser("sa");
        return ds;
    }

    @BeforeEach
    public void cleanTablesAndRequireQuietRegistry() throws Exception {
        try (java.sql.Connection c = dataSourceMsg.getConnection();
             java.sql.Statement s = c.createStatement()) {
            s.execute("DELETE FROM z_msg_message");
            s.execute("DELETE FROM z_msg_delivery_log");
            s.execute("DELETE FROM z_msg_user_preference");
        }
        // 上一条用例漏关一条连接就会污染这里的在线数与挤占断言：先等它归零，等不到就红。
        assertTrue(waitUntil(() -> registry.onlineConnections() == 0, FRAME_TIMEOUT_MS),
                "用例开始前注册表应为空，实际残留 " + registry.onlineConnections() + " 条连接");
    }

    @AfterEach
    public void closeSockets() {
        for (WebSocketSession s : sockets) {
            try {
                s.close(CloseStatus.NORMAL);
            } catch (Exception ignore) {
                // 已经被服务端挤掉或关掉了
            }
        }
        sockets.clear();
    }

    // ---------------- 造消息 ----------------

    protected Message inbox(long userId, String bizType, String title) {
        return Message.builder()
                .channel(Channels.IN_APP)
                .userId(userId)
                .receiver(String.valueOf(userId))
                .bizType(bizType)
                .subject(title)
                .content("正文:" + title)
                .build();
    }

    // ---------------- 连与发 ----------------

    protected String wsUrl() {
        return "ws://127.0.0.1:" + port + wsProperties.getPath();
    }

    protected String ticket(long userId) {
        ResponseEntity<String> resp = exchange("/api/msg/inbox/ws-token", HttpMethod.GET, userId);
        assertEquals(200, codeOf(resp), "换 ticket 应当成功，body=" + resp.getBody());
        Object token = dataOf(resp).get("token");
        assertNotNull(token, "响应里没有 token: " + resp.getBody());
        return String.valueOf(token);
    }

    protected Recorder connect(long userId) {
        return openUrl(wsUrl() + "?token=" + ticket(userId));
    }

    protected Recorder openUrl(String url) {
        Recorder rec = new Recorder();
        try {
            WebSocketSession s = wsClient.doHandshake(rec, url).get(FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            sockets.add(s);
            rec.attach(s);
            assertTrue(s.isOpen(), "握手返回了但不是打开状态: " + url);
            return rec;
        } catch (Exception e) {
            throw new AssertionError("握手失败: " + url + " -> " + e, e);
        }
    }

    /**
     * 判定"被拒"以服务端注册表为准，而不是以客户端抛没抛异常为准：客户端可能抛异常、也可能拿到
     * 一个立刻被关掉的 session，但只要服务端没登记，就是真拒了。
     */
    protected void assertHandshakeRefused(String url, String why) {
        Recorder rec = new Recorder();
        try {
            WebSocketSession s = wsClient.doHandshake(rec, url).get(REFUSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            sockets.add(s);
            try {
                s.close(CloseStatus.NORMAL);
            } catch (Exception ignore) {
                // 已经关了
            }
        } catch (Exception expected) {
            // 握手被容器拒绝，正是我们要的
        }
        assertEquals(0, registry.onlineConnections(),
                why + "：被拒的握手不能在服务端留下任何连接，实际注册表: " + registry.onlineIds());
        assertTrue(rec.frames().isEmpty(), why + "：被拒之后不该收到任何帧: " + rec.dump());
    }

    protected void send(Recorder rec, String frame) {
        try {
            rec.session().sendMessage(new TextMessage(frame));
        } catch (Exception e) {
            throw new AssertionError("发送帧失败: " + frame, e);
        }
    }

    protected void subscribe(Recorder rec, String clientMsgId, String... topics) {
        send(rec, frameWithTopics("subscribe", clientMsgId, topics));
    }

    protected void unsubscribe(Recorder rec, String clientMsgId, String... topics) {
        send(rec, frameWithTopics("unsubscribe", clientMsgId, topics));
    }

    static String frameWithTopics(String op, String clientMsgId, String... topics) {
        StringBuilder sb = new StringBuilder("{\"op\":\"").append(op)
                .append("\",\"clientMsgId\":\"").append(clientMsgId).append("\",\"topics\":[");
        for (int i = 0; i < topics.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(topics[i]).append('"');
        }
        return sb.append("]}").toString();
    }

    // ---------------- 等帧 ----------------

    protected void awaitReady(Recorder rec) {
        awaitFrame(rec, opIs(RealtimeMessage.OP_READY), "ready 帧");
    }

    protected String connectionIdOf(Recorder rec) {
        return String.valueOf(payloadOf(awaitFrame(rec, opIs(RealtimeMessage.OP_READY),
                "ready 帧")).get("connectionId"));
    }

    /**
     * 发一帧 subscribe 并等它的 ack——成对提供，是因为"只等不发"会让用例静默挂到超时，
     * 看上去像服务端没实现（本类的第一版就踩了这个）。
     */
    protected Map<String, Object> subscribeAndWait(Recorder rec, String clientMsgId, String... topics) {
        subscribe(rec, clientMsgId, topics);
        return awaitAckRaw(rec, clientMsgId);
    }

    protected Map<String, Object> unsubscribeAndWait(Recorder rec, String clientMsgId, String... topics) {
        unsubscribe(rec, clientMsgId, topics);
        return awaitAckRaw(rec, clientMsgId);
    }

    protected Map<String, Object> awaitAckRaw(Recorder rec, final String clientMsgId) {
        return awaitFrame(rec, f -> RealtimeMessage.OP_ACK.equals(f.get("op"))
                && clientMsgId.equals(f.get("clientMsgId")), "ack(" + clientMsgId + ")");
    }

    protected Map<String, Object> awaitErrorCode(Recorder rec, final String errorCode) {
        return awaitFrame(rec, f -> errorCode.equals(f.get("errorCode")), errorCode);
    }

    protected static Map<String, Object> awaitFrame(Recorder rec,
                                                     Predicate<Map<String, Object>> p, String what) {
        final AtomicReference<Map<String, Object>> hit = new AtomicReference<Map<String, Object>>();
        boolean got = waitUntil(() -> {
            Map<String, Object> f = rec.first(p);
            if (f == null) {
                return false;
            }
            hit.set(f);
            return true;
        }, FRAME_TIMEOUT_MS);
        if (!got) {
            fail("超时未等到" + what + "。已收到: " + rec.dump());
        }
        return hit.get();
    }

    static boolean waitUntil(BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        do {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(15L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.currentTimeMillis() < deadline);
        return condition.getAsBoolean();
    }

    // ---------------- 帧的形状 ----------------

    static void assertVerbContains(Map<String, Object> ack, String verb, String topic) {
        assertTrue(stringList(payloadOf(ack).get(verb)).contains(topic),
                verb + " 应当包含 " + topic + ": " + ack);
    }

    static Predicate<Map<String, Object>> opIs(String op) {
        return f -> op.equals(f.get("op"));
    }

    static Predicate<Map<String, Object>> withTitle(String title) {
        return f -> title.equals(titleOf(f));
    }

    static Predicate<Map<String, Object>> withText(String text) {
        return f -> text.equals(textOf(f));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> payloadOf(Map<String, Object> frame) {
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

    static String titleOf(Map<String, Object> frame) {
        if (!RealtimeMessage.OP_MESSAGE.equals(frame.get("op"))) {
            return "";
        }
        Object t = payloadOf(frame).get("title");
        return t == null ? "" : String.valueOf(t);
    }

    static String textOf(Map<String, Object> frame) {
        if (!RealtimeMessage.OP_MESSAGE.equals(frame.get("op"))) {
            return "";
        }
        Object t = payloadOf(frame).get("text");
        return t == null ? "" : String.valueOf(t);
    }

    static boolean containsTitle(Recorder rec, String title) {
        for (Map<String, Object> f : rec.frames()) {
            if (title.equals(titleOf(f))) {
                return true;
            }
        }
        return false;
    }

    static boolean containsText(Recorder rec, String text) {
        for (Map<String, Object> f : rec.frames()) {
            if (text.equals(textOf(f))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    static List<String> stringList(Object raw) {
        List<String> out = new ArrayList<String>();
        if (raw instanceof List) {
            for (Object o : (List<Object>) raw) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    static Map<String, Object> one(String k, Object v) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put(k, v);
        return m;
    }

    /** 只动最后两个字符：结构仍然是一张合法形状的 ticket，但签名一定对不上。 */
    static String tamper(String token) {
        return token.substring(0, token.length() - 2) + "xy";
    }

    // ---------------- HTTP ----------------

    /**
     * 直接发一次 HTTP 握手探测，读回状态码。
     * <p>
     * 为什么不用 {@code java.net.http.HttpClient}：它把 {@code Upgrade} 列为受限头，
     * 也不认 {@code ws} scheme —— 恰好是这里唯一要验的两个头。用裸 socket 才能证到
     * "503/401 来自鉴权拦截器"，而不是来自某个我们压根没发出去的头。
     */
    static int handshakeStatus(int port, String path, boolean upgrade) throws Exception {
        StringBuilder req = new StringBuilder("GET ").append(path).append(" HTTP/1.1\r\n")
                .append("Host: 127.0.0.1:").append(port).append("\r\n");
        if (upgrade) {
            req.append("Upgrade: websocket\r\n")
                    .append("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n")
                    .append("Sec-WebSocket-Version: 13\r\n");
        }
        req.append("Connection: ").append(upgrade ? "Upgrade" : "close").append("\r\n\r\n");
        java.net.Socket socket = new java.net.Socket("127.0.0.1", port);
        socket.setSoTimeout(5000);
        try {
            socket.getOutputStream().write(req.toString().getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            java.io.BufferedReader in = new java.io.BufferedReader(
                    new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String statusLine = in.readLine();
            if (statusLine == null || !statusLine.startsWith("HTTP/")) {
                throw new AssertionError("状态行读不到，实际首行: " + statusLine);
            }
            return Integer.parseInt(statusLine.split(" ")[1]);
        } finally {
            socket.close();
        }
    }

    protected ResponseEntity<String> exchange(String path, HttpMethod method, Long userId) {
        HttpHeaders headers = new HttpHeaders();
        if (userId != null) {
            headers.set("X-Msg-User-Id", String.valueOf(userId));
        }
        headers.set("Content-Type", "application/json");
        return rest.exchange(path, method, new HttpEntity<Void>(headers), String.class);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> dataOf(ResponseEntity<String> resp) {
        Map<String, Object> body = MsgJson.toMap(resp.getBody());
        Object data = body == null ? null : body.get("data");
        assertTrue(data instanceof Map, "响应 data 不是对象: " + resp.getBody());
        return (Map<String, Object>) data;
    }

    protected int codeOf(ResponseEntity<String> resp) {
        Map<String, Object> body = MsgJson.toMap(resp.getBody());
        Object code = body == null ? null : body.get("code");
        return code == null ? -1 : ((Number) code).intValue();
    }

    /** 客户端侧的帧记录器：每帧存下来供断言，并记住服务端是否把它关了。 */
    static final class Recorder extends TextWebSocketHandler {
        private final List<Map<String, Object>> frames =
                Collections.synchronizedList(new ArrayList<Map<String, Object>>());
        private final CountDownLatch closedLatch = new CountDownLatch(1);
        private volatile WebSocketSession session;

        void attach(WebSocketSession s) {
            this.session = s;
        }

        WebSocketSession session() {
            return session;
        }

        CountDownLatch closed() {
            return closedLatch;
        }

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage message) {
            Map<String, Object> frame = MsgJson.toMap(message.getPayload());
            if (frame != null) {
                frames.add(frame);
            } else {
                Map<String, Object> m = new HashMap<String, Object>();
                m.put("__not_json__", message.getPayload());
                frames.add(m);
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
            closedLatch.countDown();
        }

        List<Map<String, Object>> frames() {
            return new ArrayList<Map<String, Object>>(frames);
        }

        Map<String, Object> first(Predicate<Map<String, Object>> p) {
            for (Map<String, Object> f : frames()) {
                if (p.test(f)) {
                    return f;
                }
            }
            return null;
        }

        String dump() {
            return MsgJson.toJson(frames());
        }
    }
}
