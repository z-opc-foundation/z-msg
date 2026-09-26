package com.zifang.z.msg.web;

import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageGateway;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.core.json.MsgJson;
import com.zifang.z.msg.core.realtime.RealtimeTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 收件箱与通道自省的端到端用例，跑在真 H2 + 真 Tomcat 上（表结构直接执行 core 里那份
 * {@code z-msg/sql/schema-h2.sql}，所以这里验的 schema 和被 SchemaParityTest 守护的是同一份）。
 * <p>
 * 每个"应当拒绝"的用例都配了一个"同样的请求换个身份就要成功"的对照，
 * 否则 404 / 空列表 / 恒 false 都能让断言假绿。
 */
@SpringBootTest(classes = MsgWebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "z-msg.enabled=true",
        "z-msg.web.trusted-header-enabled=true",
        "z-msg.realtime.ticket-secret=integration-test-secret-0123456789abcdef",
        "z-msg.inbox.default-page-size=50",
        "z.msg.test.db=msg_inbox_it"
})
public class MsgInboxApiTest {

    private static final long USER_A = 9001L;
    private static final long USER_B = 9002L;

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private MessageGateway gateway;
    @Autowired
    private RealtimeTicketService ticketService;
    @javax.annotation.Resource(name = "dataSourceMsg")
    private DataSource dataSourceMsg;

    /**
     * 同一进程内所有用例共用一台 H2，不清表就会互相污染（红点增量、标题查找都会串味）。
     */
    @org.junit.jupiter.api.BeforeEach
    public void cleanTables() throws Exception {
        try (java.sql.Connection c = dataSourceMsg.getConnection();
             java.sql.Statement s = c.createStatement()) {
            s.execute("DELETE FROM z_msg_message");
            s.execute("DELETE FROM z_msg_delivery_log");
            s.execute("DELETE FROM z_msg_user_preference");
        }
    }

    // ---------------- 投递侧：IN_APP 必须真的能走通 ----------------

    @Test
    public void inAppDeliveryPersistsAndIsReadableThroughTheApi() {
        MessageSendResult r = gateway.send(inbox(USER_A, "ORDER_PAID-1", "A 的订单已支付"));
        assertTrue(r.isSuccess(), "IN_APP 必须能经 ChannelRouter 投递，实际失败: "
                + r.getErrorCode() + " / " + r.getErrorMessage());

        List<Map<String, Object>> records = pageRecords("/api/msg/inbox/list", USER_A);
        assertTrue(containsTitle(records, "A 的订单已支付"),
                "刚投的站内信应当出现在自己的列表里，实际: " + titles(records));
    }

    // ---------------- 身份 ----------------

    @Test
    public void anonymousCallsAreRejectedButAuthenticatedOnesSucceed() {
        ResponseEntity<String> anon = exchange("/api/msg/inbox/list", HttpMethod.GET, null);
        assertEquals(HttpStatus.UNAUTHORIZED, anon.getStatusCode(),
                "没有身份必须 401，实际 " + anon.getStatusCode() + " body=" + anon.getBody());

        // 同一个 URL 换个身份就 200 —— 证明上面那条 401 来自鉴权而不是路径不存在
        ResponseEntity<String> authed = exchange("/api/msg/inbox/list", HttpMethod.GET, USER_A);
        assertEquals(HttpStatus.OK, authed.getStatusCode(), "对照请求应当 200: " + authed.getBody());
        assertTrue(dataOf(authed) != null);
    }

    // ---------------- 越权 ----------------

    @Test
    public void oneUserCannotSeeOrMutateAnotherUsersInbox() {
        gateway.send(inbox(USER_A, "PRIV-1", "只有 A 能看到的消息"));

        List<Map<String, Object>> bList = pageRecords("/api/msg/inbox/list", USER_B);
        assertFalse(containsTitle(bList, "只有 A 能看到的消息"),
                "B 的列表里不该出现 A 的消息，实际: " + titles(bList));

        Long aMsgId = idOfTitle(pageRecords("/api/msg/inbox/list", USER_A), "只有 A 能看到的消息");
        assertNotNull(aMsgId, "前置：A 自己能看到这条，否则后面的越权断言没有猎物");

        // B 去读 A 的那条：与"不存在"同形，不给探测
        ResponseEntity<String> denied = exchange("/api/msg/inbox/detail?id=" + aMsgId,
                HttpMethod.GET, USER_B);
        assertEquals(404, codeOf(denied), "跨用户读详情必须按不存在处理，body=" + denied.getBody());

        // B 去标已读：改不动
        ResponseEntity<String> markBy = exchange("/api/msg/inbox/read?id=" + aMsgId,
                HttpMethod.POST, USER_B);
        assertEquals(Boolean.FALSE, boolData(markBy), "B 标已读 A 的消息必须返回 false");

        // A 仍然看到它未读 —— 证明上一步真的什么都没改
        assertTrue(isUnread(aMsgId, USER_A), "A 侧这条应仍未读");

        // B 去删：删不掉；A 自己删才成功
        assertEquals(Boolean.FALSE, boolData(exchange("/api/msg/inbox/delete?id=" + aMsgId,
                HttpMethod.DELETE, USER_B)), "B 删 A 的消息必须返回 false");
        assertTrue(containsTitle(pageRecords("/api/msg/inbox/list", USER_A), "只有 A 能看到的消息"),
                "B 的删除尝试不能改变 A 的列表");

        assertEquals(Boolean.TRUE, boolData(exchange("/api/msg/inbox/delete?id=" + aMsgId,
                HttpMethod.DELETE, USER_A)), "A 自己删要成功");
        assertFalse(containsTitle(pageRecords("/api/msg/inbox/list", USER_A), "只有 A 能看到的消息"),
                "A 删除后自己的列表不该再有这条");
    }

    // ---------------- 过期与红点口径 ----------------

    @Test
    public void expiredMessagesAreNeitherListedNorCountedAsUnread() {
        long before = unreadCount(USER_A);
        gateway.send(Message.builder()
                .channel(Channels.IN_APP)
                .userId(USER_A)
                .receiver(String.valueOf(USER_A))
                .bizType("EXP-1")
                .subject("已过期的消息")
                .content("正文:已过期的消息")
                .expireAt(LocalDateTime.now().minusMinutes(5))
                .build());
        assertEquals(before, unreadCount(USER_A),
                "过期消息不该计入红点（这条断言的猎物是同批里那条未过期的，见下一条用例）");
        assertFalse(containsTitle(pageRecords("/api/msg/inbox/list", USER_A), "已过期的消息"),
                "过期消息不该出现在列表里");

        gateway.send(inbox(USER_A, "LIVE-1", "未过期的消息"));
        assertEquals(before + 1, unreadCount(USER_A),
                "未过期消息必须让红点 +1，否则上一条的\"没变\"只是因为整条链路都坏");
    }

    // ---------------- 分页 ----------------

    @Test
    public void pagingActuallyTruncatesAndReportsRealTotal() {
        for (int i = 0; i < 5; i++) {
            gateway.send(inbox(USER_B, "PAGE-" + i, "分页样本 " + i));
        }
        ResponseEntity<String> resp = exchange("/api/msg/inbox/list?page=1&size=2",
                HttpMethod.GET, USER_B);
        Map<String, Object> data = dataOf(resp);
        assertEquals(2, records(data).size(), "size=2 必须真的只回 2 条");
        // 1.0.0 没挂分页插件：total 恒 0、records 是全表。这里两头都钉住。
        assertTrue(((Number) data.get("total")).longValue() >= 5L,
                "total 必须是真实条数而不是 0，实际: " + data.get("total"));
    }

    // ---------------- 幂等 ----------------

    @Test
    public void sameIdempotencyKeyDoesNotDoubleInsert() {
        long before = unreadCount(USER_A);
        Message a = inbox(USER_A, "DEDUP-A", "重复投递样本");
        a.setIdempotencyKey("same-key-1");
        Message b = inbox(USER_A, "DEDUP-B", "重复投递样本 2");
        b.setIdempotencyKey("same-key-1");
        gateway.send(a);
        gateway.send(b);
        assertEquals(before + 1, unreadCount(USER_A),
                "同一 dedupKey 投两次只应落一条");
    }

    // ---------------- 通道自省 ----------------

    @Test
    @SuppressWarnings("unchecked")
    public void channelIntrospectionLabelsMockProvidersHonestly() {
        ResponseEntity<String> resp = exchange("/api/msg/channel/list", HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, resp.getStatusCode(), resp.getBody());
        List<Object> rows = (List<Object>) rawData(resp);
        Map<String, Object> inApp = rowFor(rows, Channels.IN_APP);
        Map<String, Object> sms = rowFor(rows, Channels.SMS);

        assertEquals(Boolean.TRUE, inApp.get("real"), "IN_APP 有真的 db provider");
        assertFalse(Boolean.TRUE.equals(sms.get("real")),
                "没配厂商时 SMS 不该被报成\"真通道\"，实际: " + sms);
        assertEquals("mock", sms.get("activeProvider"), "SMS 当前应落在 mock 上");
    }

    // ---------------- ws-ticket ----------------

    @Test
    public void wsTokenIsBoundToTheAuthenticatedUser() {
        ResponseEntity<String> anon = exchange("/api/msg/inbox/ws-token", HttpMethod.GET, null);
        assertEquals(HttpStatus.UNAUTHORIZED, anon.getStatusCode(), "换 ticket 必须先有身份");

        Map<String, Object> data = dataOf(exchange("/api/msg/inbox/ws-token", HttpMethod.GET, USER_A));
        String token = (String) data.get("token");
        assertNotNull(token);
        assertEquals(Long.valueOf(USER_A), ticketService.verify(token),
                "ticket 解出来的身份必须是申请者本人");
        org.junit.jupiter.api.Assertions.assertNull(
                ticketService.verify(token.substring(0, token.length() - 2) + "xy"),
                "改过签名的 ticket 必须验不过");
    }

    // ---------------- helpers ----------------

    private Message inbox(long userId, String bizType, String title) {
        return Message.builder()
                .channel(Channels.IN_APP)
                .userId(userId)
                .receiver(String.valueOf(userId))
                .bizType(bizType)
                .subject(title)
                .content("正文:" + title)
                .build();
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Long userId) {
        HttpHeaders headers = new HttpHeaders();
        if (userId != null) {
            headers.set("X-Msg-User-Id", String.valueOf(userId));
        }
        headers.set("Content-Type", "application/json");
        return rest.exchange(path, method, new HttpEntity<Void>(headers), String.class);
    }

    /** {@code Result.data} 的原始形态：可能是分页对象、可能是标量、也可能是数组，调用方自己判。 */
    private Object rawData(ResponseEntity<String> resp) {
        Map<String, Object> body = MsgJson.toMap(resp.getBody());
        return body == null ? null : body.get("data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(ResponseEntity<String> resp) {
        Object data = rawData(resp);
        if (data == null) {
            return null;
        }
        assertTrue(data instanceof Map, "该端点的 data 应是对象，实际是 "
                + data.getClass().getSimpleName() + " => " + data);
        return (Map<String, Object>) data;
    }

    @SuppressWarnings("unchecked")
    private int codeOf(ResponseEntity<String> resp) {
        Map<String, Object> body = MsgJson.toMap(resp.getBody());
        Object code = body == null ? null : body.get("code");
        return code == null ? -1 : ((Number) code).intValue();
    }

    /** 这几个端点的 data 是标量（Boolean/Integer），不能过 dataOf 的对象断言。 */
    private Object boolData(ResponseEntity<String> resp) {
        return rawData(resp);
    }

    @SuppressWarnings("unchecked")
    private List<Object> records(Map<String, Object> page) {
        return page == null ? null : (List<Object>) page.get("records");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pageRecords(String path, Long userId) {
        Map<String, Object> page = dataOf(exchange(path, HttpMethod.GET, userId));
        assertNotNull(page, "分页响应为空: " + path);
        List<Object> raw = (List<Object>) page.get("records");
        return raw == null ? java.util.Collections.<Map<String, Object>>emptyList()
                : (List<Map<String, Object>>) (List<?>) raw;
    }

    private long unreadCount(long userId) {
        Object n = rawData(exchange("/api/msg/inbox/unread-count", HttpMethod.GET, userId));
        return n == null ? -1L : ((Number) n).longValue();
    }

    private boolean isUnread(Long msgId, long userId) {
        for (Map<String, Object> row : pageRecords("/api/msg/inbox/list", userId)) {
            if (msgId.equals(((Number) row.get("id")).longValue())) {
                return ((Number) row.get("isRead")).intValue() == 0;
            }
        }
        return false;
    }

    private boolean containsTitle(List<Map<String, Object>> rows, String title) {
        return titles(rows).contains(title);
    }

    private Set<String> titles(List<Map<String, Object>> rows) {
        Set<String> out = new HashSet<String>();
        for (Map<String, Object> row : rows) {
            out.add(String.valueOf(row.get("title")));
        }
        return out;
    }

    private Long idOfTitle(List<Map<String, Object>> rows, String title) {
        for (Map<String, Object> row : rows) {
            if (title.equals(row.get("title"))) {
                return ((Number) row.get("id")).longValue();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rowFor(List<Object> rows, String channel) {
        for (Object o : rows) {
            Map<String, Object> row = (Map<String, Object>) o;
            if (channel.equals(row.get("channel"))) {
                return row;
            }
        }
        throw new AssertionError("通道自省里没有 " + channel + " 这一行: " + rows);
    }
}
