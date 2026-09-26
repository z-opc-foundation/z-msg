package com.zifang.z.msg.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 这个测试就是文档里那句"五分钟"的凭据：不 mock 任何东西，真起 Tomcat + 真 H2 +
 * 两条真 WebSocket，让 1001 说的话出现在 1002 的帧里，让站内信在对面的红点上跳一下。
 * <p>
 * 本类不声明任何 {@code @Bean}/{@code @MapperScan}：z-msg 的 bean 全靠各模块自己的
 * {@code spring.factories} 挂上来。装配漏一环（比如 ws 少了 {@code @EnableWebSocket}）
 * 这里立刻红 —— 手动把 bean 塞进测试上下文就验不到这一层了。
 * <p>
 * 等待一律轮询到截止，不用固定 sleep；每条"应当被拒"的断言在同一条用例里都配了
 * "换个合法入参就要成功"的对照，否则端点压根没挂上也能绿。
 */
@SpringBootTest(classes = MsgExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MsgExampleApplicationTest {

    private static final long USER_A = 1001L;
    private static final long USER_B = 1002L;
    private static final String LOBBY = "room:lobby";
    private static final String STRANGER_ROOM = "room:4242";
    private static final long TIMEOUT_MS = 10_000L;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TestRestTemplate rest;
    @LocalServerPort
    private int port;
    @javax.annotation.Resource(name = "dataSourceMsg")
    private DataSource dataSourceMsg;

    private final StandardWebSocketClient wsClient = new StandardWebSocketClient();
    private final List<WebSocketHolder> holders = new ArrayList<WebSocketHolder>();

    // ---------------------------------------------------------------- 生命周期

    @BeforeEach
    public void cleanTables() throws Exception {
        try (java.sql.Connection c = dataSourceMsg.getConnection();
             java.sql.Statement s = c.createStatement()) {
            s.execute("DELETE FROM z_msg_message");
            s.execute("DELETE FROM z_msg_delivery_log");
            // 上一例留下的 IM 行会把这一例的未读数/会话列表带偏，所以四张表一起清
            s.execute("DELETE FROM z_msg_im_read_receipt");
            s.execute("DELETE FROM z_msg_im_message");
            s.execute("DELETE FROM z_msg_im_member");
            s.execute("DELETE FROM z_msg_im_conversation");
        }
    }

    @AfterEach
    public void closeEverything() {
        for (WebSocketHolder h : holders) {
            try {
                h.session.close(CloseStatus.NORMAL);
            } catch (Exception ignore) {
                // 服务端已经先摘掉了
            }
            post("/demo/logout", h.sessionHeaders());
        }
        holders.clear();
    }

    // ---------------------------------------------------------------- 登录态

    /** 一次演示登录：身份落在服务端 session，之后所有请求只认这张 cookie */
    private static final class Session {
        private final HttpHeaders headers = new HttpHeaders();

        HttpHeaders headers() {
            return headers;
        }
    }

    private Session login(long userId) {
        Session s = new Session();
        ResponseEntity<String> resp = rest.exchange("/demo/login?as=" + userId, HttpMethod.POST,
                new HttpEntity<Void>(s.headers()), String.class);
        assertEquals(200, resp.getStatusCodeValue(), "演示登录要 200");
        JsonNode body = read(resp.getBody());
        assertNotNull(body, "登录响应不是 JSON: " + resp.getBody());
        assertTrue(body.path("success").asBoolean(), "登录失败: " + resp.getBody());
        String setCookie = resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie, "身份必须落在服务端 session 上，否则后面的 401 断言没有意义");
        s.headers().set(HttpHeaders.COOKIE, setCookie.split(";", 2)[0]);
        return s;
    }

    /**
     * 往【同一把】cookie jar 里再登一个身份，只用 tab 头区分——这就是"同一个浏览器开了第二个标签页"
     * 的真实形状。第二次登录不会重新发 cookie（服务端复用同一个 session），所以 jar 里始终是那一条。
     */
    private void loginInto(Session jar, String tab, long userId) {
        ResponseEntity<String> resp = rest.exchange("/demo/login?as=" + userId, HttpMethod.POST,
                new HttpEntity<Void>(tabHeaders(jar, tab)), String.class);
        assertEquals(200, resp.getStatusCodeValue(), "演示登录要 200: " + resp.getBody());
        assertTrue(read(resp.getBody()).path("success").asBoolean(), "登录失败: " + resp.getBody());
        String setCookie = resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        if (setCookie != null) {
            jar.headers().set(HttpHeaders.COOKIE, setCookie.split(";", 2)[0]);
        }
        assertNotNull(jar.headers().getFirst(HttpHeaders.COOKIE),
                "两次登录必须共用同一把 cookie jar，否则这一例量的不是同一个浏览器");
    }

    // ---------------------------------------------------------------- 连接

    private static final class Recorder extends TextWebSocketHandler {
        final List<String> frames = new CopyOnWriteArrayList<String>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            frames.add(message.getPayload());
        }

        String dump() {
            return frames.toString();
        }
    }

    private static final class WebSocketHolder {
        final WebSocketSession session;
        final Recorder rec;
        final long userId;
        final String name;
        final Session http;

        WebSocketHolder(WebSocketSession session, Recorder rec, long userId, String name, Session http) {
            this.session = session;
            this.rec = rec;
            this.userId = userId;
            this.name = name;
            this.http = http;
        }

        HttpHeaders sessionHeaders() {
            return http.headers();
        }

        void publish(String topic, String text, String clientMsgId) {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("userId", userId);
            payload.put("from", name);
            payload.put("text", text);
            Map<String, Object> frame = new LinkedHashMap<String, Object>();
            frame.put("op", "publish");
            frame.put("clientMsgId", clientMsgId);
            frame.put("topic", topic);
            frame.put("kind", "chat");
            frame.put("payload", payload);
            try {
                session.sendMessage(new TextMessage(JSON.writeValueAsString(frame)));
            } catch (Exception e) {
                throw new AssertionError("发帧失败: " + e, e);
            }
        }
    }

    /** 登录 → 换短期票 → 真握手 → 等到 ready */
    private WebSocketHolder open(long userId, String name) {
        Session session = login(userId);
        ResponseEntity<String> token = rest.exchange("/api/msg/inbox/ws-token", HttpMethod.GET,
                new HttpEntity<Void>(session.headers()), String.class);
        assertEquals(200, token.getStatusCodeValue(), "登录后换票要成功: " + token.getBody());
        JsonNode body = read(token.getBody());
        String ticket = body.path("data").path("token").asText();
        assertTrue(!ticket.isEmpty(), "票不能是空串: " + token.getBody());
        try {
            Recorder rec = new Recorder();
            WebSocketSession raw = wsClient.doHandshake(rec,
                    "ws://127.0.0.1:" + port + "/api/msg/ws?token=" + ticket).get();
            WebSocketHolder holder = new WebSocketHolder(raw, rec, userId, name, session);
            holders.add(holder);
            awaitFrame(rec, op("ready"), "ready 帧");
            return holder;
        } catch (Exception e) {
            throw new AssertionError("握手失败: " + e, e);
        }
    }

    // ---------------------------------------------------------------- 取帧

    private static Predicate<JsonNode> op(final String value) {
        return f -> value.equals(f.path("op").asText());
    }

    private JsonNode awaitFrame(Recorder rec, Predicate<JsonNode> match, String what) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            for (String raw : rec.frames) {
                JsonNode f = read(raw);
                if (f != null && match.test(f)) {
                    return f;
                }
            }
            try {
                Thread.sleep(15L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail("等不到" + what + "，已收到: " + rec.dump());
        return null;
    }

    private static JsonNode payloadOf(JsonNode frame) {
        // payload 是 JSON 字符串（传输层不理解其结构），所以要对它再解析一次
        return read(frame.path("payload").asText());
    }

    private static JsonNode read(String text) {
        try {
            return text == null ? null : JSON.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    private ResponseEntity<String> post(String path, HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<Void>(headers), String.class);
    }

    private long unread(Session session) {
        return unread(session.headers());
    }

    private long unread(HttpHeaders headers) {
        ResponseEntity<String> resp = rest.exchange("/api/msg/inbox/unread-count", HttpMethod.GET,
                new HttpEntity<Void>(headers), String.class);
        assertEquals(200, resp.getStatusCodeValue(), "取未读数应当成功: " + resp.getBody());
        return read(resp.getBody()).path("data").asLong();
    }

    /** 同一个 cookie jar 再加一把 tab 头：等价于"同一个浏览器的第二个标签页"。 */
    private HttpHeaders tabHeaders(Session jar, String tab) {
        HttpHeaders h = new HttpHeaders();
        h.putAll(jar.headers());
        if (tab != null) {
            h.set("X-Demo-Tab", tab);
        }
        return h;
    }

    /** 以 {@code from} 的身份给 {@code to} 塞一条站内信；投递失败本身要先红，别让它拖到后面的计数断言。 */
    private void notify(HttpHeaders from, long to, String title) {
        ResponseEntity<String> resp = post("/demo/notify?to=" + to + "&title=" + title, from);
        assertEquals(200, resp.getStatusCodeValue(), "发站内信要 200: " + resp.getBody());
        assertTrue(read(resp.getBody()).path("data").path("success").asBoolean(),
                "站内信必须真的投递成功，否则后面的未读断言没有数据: " + resp.getBody());
    }

    private void notify(Session from, long to, String title) {
        notify(from.headers(), to, title);
    }

    // ---------------------------------------------------------------- 用例

    @Test
    public void twoRealSocketsExchangeLobbyMessages() {
        WebSocketHolder a = open(USER_A, "张三");
        WebSocketHolder b = open(USER_B, "李四");

        a.publish(LOBBY, "今晚八点上线", "a1");

        JsonNode ack = awaitFrame(a.rec, f -> "ack".equals(f.path("op").asText())
                && "a1".equals(f.path("clientMsgId").asText()), "代发 ack");
        JsonNode info = payloadOf(ack);
        assertTrue(info.path("delivered").asInt() >= 1,
                "命中数要数得出来，否则 publish 根本没投递: " + info);

        JsonNode got = awaitFrame(b.rec, f -> "message".equals(f.path("op").asText())
                && "chat".equals(f.path("kind").asText()), "B 的聊天帧");
        assertEquals(LOBBY, got.path("topic").asText());
        assertEquals("今晚八点上线", payloadOf(got).path("text").asText());
        assertEquals("张三", payloadOf(got).path("from").asText());

        // 反向也走得通：不是一条单向管道
        b.publish(LOBBY, "收到", "b1");
        JsonNode back = awaitFrame(a.rec, f -> "message".equals(f.path("op").asText())
                && "收到".equals(payloadOf(f).path("text").asText()), "A 的回复帧");
        assertEquals(LOBBY, back.path("topic").asText());
    }

    @Test
    public void onlyTheLobbyIsOpenForSpeakingWhileOtherRoomsStayVetoed() {
        WebSocketHolder a = open(USER_A, "张三");

        a.publish(STRANGER_ROOM, "闯进来的", "x1");
        JsonNode err = awaitFrame(a.rec, f -> "error".equals(f.path("op").asText())
                && "x1".equals(f.path("clientMsgId").asText()), "越权 error");
        assertEquals("WS_TOPIC_FORBIDDEN", err.path("errorCode").asText(),
                "没有策略表态的 room 必须被写侧默认策略拒掉，收到: " + a.rec.dump());

        // 同一条连接上换个被策略放行的 topic 就要成功：否则上面那条 error 可能压根是别的原因
        a.publish(LOBBY, "在大厅里说话", "x2");
        awaitFrame(a.rec, f -> "ack".equals(f.path("op").asText())
                && "x2".equals(f.path("clientMsgId").asText()), "大厅代发 ack");
    }

    @Test
    public void inboxMessageReachesTheOtherUsersSocketAndItsUnreadCount() {
        WebSocketHolder a = open(USER_A, "张三");
        WebSocketHolder b = open(USER_B, "李四");

        assertEquals(0L, unread(b.http), "开演前 B 的未读应为 0，否则下面的 +1 没有对照");

        ResponseEntity<String> sent = post("/demo/notify?to=" + USER_B + "&title=订单已发货", a.sessionHeaders());
        assertEquals(200, sent.getStatusCodeValue());
        JsonNode sentBody = read(sent.getBody());
        assertTrue(sentBody.path("data").path("success").asBoolean(),
                "站内信必须投递成功，否则收帧断言没有猎物: " + sent.getBody());

        JsonNode push = awaitFrame(b.rec, f -> "message".equals(f.path("op").asText())
                && "inbox".equals(f.path("kind").asText()), "B 的站内信帧");
        JsonNode mail = payloadOf(push);
        assertEquals("订单已发货", mail.path("title").asText());
        assertTrue(mail.path("unread").asBoolean(), "帧里要带 unread 标记，前端才敢直接加红点: " + mail);
        assertEquals("user:" + USER_B, push.path("topic").asText());
        assertEquals(1L, unread(b.http), "红点要当场加一");

        ResponseEntity<String> list = rest.exchange("/api/msg/inbox/list?page=1&size=20", HttpMethod.GET,
                new HttpEntity<Void>(b.sessionHeaders()), String.class);
        JsonNode rows = read(list.getBody()).path("data").path("records");
        assertEquals(1, rows.size(), "收件箱里就该有这一条: " + list.getBody());
        long id = rows.get(0).path("id").asLong();
        assertEquals(0, rows.get(0).path("isRead").asInt(), "还没读过");

        post("/api/msg/inbox/read?id=" + id, b.sessionHeaders());
        assertEquals(0L, unread(b.http), "标已读之后红点要落回去");
        assertTrue(a.session.isOpen(), "对端的一次投递不该把别人的连接带走");
    }

    @Test
    public void protectedEndpointsTrustTheSessionNotAClientClaimedUserId() {
        HttpHeaders anon = new HttpHeaders();
        assertEquals(401, rest.exchange("/api/msg/inbox/unread-count", HttpMethod.GET,
                new HttpEntity<Void>(anon), String.class).getStatusCodeValue());
        assertEquals(401, rest.exchange("/api/msg/inbox/ws-token", HttpMethod.GET,
                new HttpEntity<Void>(anon), String.class).getStatusCodeValue());

        Session a = login(USER_A);
        Session b = login(USER_B);

        // 先把两份未读造成【不同】的数：A 两条、B 一条。
        // 这一步是下面两条注入断言的猎物：两个人都是 0 的时候，端点信不信 query 里的
        // userId 都会同样绿，而 unread-count 返回的是条数、不是 userId，
        // 拿它去等于 USER_A 只会得到一个假失败。
        notify(a, USER_A, "A 的第一条");
        notify(a, USER_A, "A 的第二条");
        notify(b, USER_B, "B 的唯一一条");
        assertEquals(2L, unread(a), "前置：A 自己有两条未读");
        assertEquals(1L, unread(b), "前置：B 必须只有一条，两个数不相等才测得出端点在信谁");

        // 登录后同一个请求就要 200，且答的是 session 里那个人，
        // 不是 query 里自称的那个人（1.0.x 的越权就出在相信后者）
        ResponseEntity<String> claimed = rest.exchange(
                "/api/msg/inbox/unread-count?userId=" + USER_B, HttpMethod.GET,
                new HttpEntity<Void>(a.headers()), String.class);
        assertEquals(200, claimed.getStatusCodeValue());
        assertEquals(2L, read(claimed.getBody()).path("data").asLong(),
                "自称 userId=1002 却拿到 B 的那 1 条，说明端点开始信客户端了: " + claimed.getBody());

        ResponseEntity<String> mine = rest.exchange("/api/msg/inbox/list?page=1&size=20&userId=" + USER_B,
                HttpMethod.GET, new HttpEntity<Void>(a.headers()), String.class);
        assertEquals(200, mine.getStatusCodeValue());
        JsonNode rows = read(mine.getBody()).path("data").path("records");
        assertEquals(2, rows.size(), "A 的收件箱里就该有自己那两条，0 行的话下面那条【不许出现别人的行】是空跑: "
                + mine.getBody());
        for (JsonNode row : rows) {
            assertEquals(USER_A, row.path("userId").asLong(),
                    "列表里不许出现别人的行: " + mine.getBody());
        }
    }

    @Test
    public void demoPageAndChannelIntrospectionAreServed() {
        ResponseEntity<String> page = rest.exchange("/", HttpMethod.GET,
                new HttpEntity<Void>(new HttpHeaders()), String.class);
        assertEquals(200, page.getStatusCodeValue(), "示例页要能打开");
        assertTrue(page.getBody().contains("大厅聊天室"), "首页应是那个聊天室演示页: " + page.getBody());

        ResponseEntity<String> channels = rest.exchange("/api/msg/channel/list", HttpMethod.GET,
                new HttpEntity<Void>(new HttpHeaders()), String.class);
        assertEquals(200, channels.getStatusCodeValue());
        JsonNode rows = read(channels.getBody()).path("data");
        boolean inAppReal = false;
        for (JsonNode row : rows) {
            if ("IN_APP".equals(row.path("channel").asText())) {
                inAppReal = row.path("real").asBoolean();
            }
        }
        assertTrue(inAppReal, "示例应用里 IN_APP 必须是真通道，否则站内信用例是空跑");
    }

    /**
     * 同一个浏览器的两个标签页，必须还是两个人。
     * <p>
     * 页面提示写的是"开两个标签页分别登 1001 / 1002"，而一个浏览器只有一个 cookie jar：
     * 只靠 HttpSession 时，后一次登录会把前一个标签的身份改掉（实测同一把 jar 里登 1001
     * 未读是 2、接着登 1002 就答 1）。这条用例刻意复用同一个 {@link Session}、只换
     * {@code X-Demo-Tab}，量的就是那个形状，而不是"两个各自独立的 jar 各自对"。
     */
    @Test
    public void twoTabsInOneBrowserStayTwoDifferentPeople() {
        Session jar = new Session();
        String tabA = "tab-a-" + System.nanoTime();
        String tabB = "tab-b-" + System.nanoTime();
        loginInto(jar, tabA, USER_A);
        loginInto(jar, tabB, USER_B);

        HttpHeaders a = tabHeaders(jar, tabA);
        HttpHeaders b = tabHeaders(jar, tabB);
        // 两人的未读造成不同的数：一样多的话，"tab 头被忽略、两个标签答成同一个人"也能绿
        notify(a, USER_A, "标签 A 的第一条");
        notify(a, USER_A, "标签 A 的第二条");
        notify(b, USER_B, "标签 B 的唯一一条");
        assertEquals(2L, unread(a), "标签 A 只能看见 1001 自己那两条");
        assertEquals(1L, unread(b), "标签 B 只能看见 1002 自己那一条");

        // 反向：tab 号只能是一把【已登记】的句柄，不能是自称。陌生 tab 不许借同一把 jar 的身份，
        // 否则这个头就退化成 1.0.0 那种"客户端报一个 userId 就当成谁"。
        ResponseEntity<String> bogus = rest.exchange("/api/msg/inbox/unread-count", HttpMethod.GET,
                new HttpEntity<Void>(tabHeaders(jar, "tab-never-registered-" + System.nanoTime())),
                String.class);
        assertEquals(401, bogus.getStatusCodeValue(),
                "没登记过的 tab 号必须按未登录处理: " + bogus.getBody());
    }
}
