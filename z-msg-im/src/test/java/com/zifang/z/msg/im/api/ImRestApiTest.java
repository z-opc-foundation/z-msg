package com.zifang.z.msg.im.api;

import com.zifang.z.msg.im.ImSpringTestSupport;
import com.zifang.z.msg.im.domain.mapper.ImConversationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /api/msg/im/**} 的 REST 形状与鉴权面。
 * <p>
 * 两条最要紧的：
 * <ol>
 *   <li><b>身份只来自登录凭证</b>。请求体里自报的 {@code userId} / {@code senderUserId}
 *       必须被忽略——1.0.0 的越权面就是"body 里写谁就是谁"。这条要正反两面都钉住：
 *       自报的字段既不能生效，也不能把合法调用打断；</li>
 *   <li><b>401/403/404/400 各归各的 advice</b>。特别是 403：web 层有个 {@code Exception.class}
 *       的兜底 advice，本模块的 {@code ImApiExceptionHandler} 排不上序的话，越权会以 500 出现。</li>
 * </ol>
 */
public class ImRestApiTest extends ImSpringTestSupport {

    private static final String CONV = "/api/msg/im/conversation";
    private static final String MSG = "/api/msg/im/message";
    private static final String READ = "/api/msg/im/read";

    /** 只为造"成员行还在、会话行没了"这个状态：直接删会话行，不走任何手工接线。 */
    @Resource
    private ImConversationMapper conversationMapper;

    // ---------------------------------------------------------------- 身份

    @Test
    public void everyEndpointAnswers401WithoutCredentialsAndNot401WithThem() {
        Map<String, String> endpoints = allEndpoints();
        for (Map.Entry<String, String> e : endpoints.entrySet()) {
            // 未登录这一轮走 ImSpringTestSupport#postJsonWithoutCredentials：同一份 body、
            // 只少身份 header。TestRestTemplate 的默认 JDK 客户端读"带 body 的 401"会抛
            // HttpRetryException，那是客户端的限制，不是本模块的行为。
            ResponseEntity<String> anon = postJsonWithoutCredentials(e.getKey(), e.getValue());
            assertEquals(401, anon.getStatusCodeValue(),
                    e.getKey() + " 未登录必须 401，实际 " + anon.getStatusCodeValue() + " " + anon.getBody());
            assertEquals(401, codeOf(anon), e.getKey() + " 的 Result.code 也要是 401: " + anon.getBody());
        }
        // 对照：同一批 endpoint 带上身份就都不再 401 —— 否则"401"可能只是端点压根没注册。
        // /leave 排在 map 的最后一条：它会把调用者从会话里摘掉，放前面的话后面的断言就都失效了。
        // 这条对照会话必须是 A 当家主的群：single() 建的单聊里 A 只是 MEMBER，
        // /member/add 与 /member/remove 回的是 403（"你没资格"，不是"端点没注册"），
        // 拿单聊当对照就永远证不出"带身份就成功"。
        Long conv = conversationService.createGroup(A, Collections.singletonList(B),
                "全面对照群", null, null, "GROUP").getId();
        int checked = 0;
        for (Map.Entry<String, String> e : endpoints.entrySet()) {
            String body = e.getValue().replace("__CONV__", String.valueOf(conv));
            ResponseEntity<String> resp = postJson(e.getKey(), A, body);
            assertTrue(resp.getStatusCodeValue() != 401,
                    e.getKey() + " 带身份之后不该再 401: " + resp.getBody());
            assertEquals(200, codeOf(resp), e.getKey() + " 带身份应当成功: " + resp.getBody());
            checked++;
        }
        assertEquals(endpoints.size(), checked, "对照循环必须真的跑完每一条");
        assertFalse(conversationService.isMember(conv, A), "/leave 是最后一条，跑完 A 应该已经退掉");
    }

    @Test
    public void selfReportedIdentityInBodyIsIgnoredNotHonoredAndNotFatal() {
        // body 里塞 userId 想冒充 OUTSIDER 开单聊 —— 会话必须是 header 里的 A 与 body 里的 peer
        ResponseEntity<String> resp = postJson(CONV + "/single", A,
                json(map("peerUserId", B, "userId", OUTSIDER, "operatorUserId", OUTSIDER)));
        assertOk(resp);
        Map<String, Object> data = dataMap(resp);
        Long conv = longOf(data.get("id"));
        assertTrue(conversationService.isMember(conv, A) && conversationService.isMember(conv, B),
                "会话属于 header 里的 A 和 body 里的 peer: " + data);
        assertFalse(conversationService.isMember(conv, OUTSIDER), "自报的 userId 不能把人塞进成员表");
        assertEquals(2L, longOf(data.get("memberCount")));

        // 发消息时自报 senderUserId=OUTSIDER（他根本不是成员，若被采纳必然 403）
        ResponseEntity<String> send = postJson(MSG + "/send", A,
                json(map("conversationId", conv, "content", "自报发送者", "senderUserId", OUTSIDER)));
        assertOk(send, "自报字段只是被忽略，不能把合法请求打断");
        Map<String, Object> msg = dataMap(send);
        assertEquals(A, longOf(msg.get("senderUserId")), "落库的发送者必须是登录身份");
        assertEquals(1L, longOf(msg.get("seq")));

        // 对照：换一个真正不是成员的人拿同一个 body 去发，就要被拒
        ResponseEntity<String> outsider = postJson(MSG + "/send", OUTSIDER,
                json(map("conversationId", conv, "content", "冒充")));
        assertEquals(403, outsider.getStatusCodeValue(), "非成员发言 403: " + outsider.getBody());
    }

    // ---------------------------------------------------------------- 金路

    @Test
    public void conversationAndMessageFlowOverHttp() {
        // 1. 开单聊（对端也开一次，必须是同一条）
        Map<String, Object> dm = dataMap(postJson(CONV + "/single", A, json(map("peerUserId", B))));
        Long dmConv = longOf(dm.get("id"));
        assertEquals("SINGLE", dm.get("convType"));
        Map<String, Object> sameAsPeer = dataMap(postJson(CONV + "/single", B, json(map("peerUserId", A))));
        assertEquals(dmConv, longOf(sameAsPeer.get("id")), "两个人各点一次必须落到同一个会话");

        // 2. 建群 + 成员与角色
        Map<String, Object> group = dataMap(postJson(CONV + "/group", A, json(map(
                "title", "REST 测试群", "memberUserIds", Arrays.asList(B, OUTSIDER)))));
        Long gc = longOf(group.get("id"));
        assertEquals("GROUP", group.get("convType"));
        assertEquals(3L, longOf(group.get("memberCount")));

        List<Object> members = dataOf(postJson(CONV + "/members", A, json(map("conversationId", gc))));
        assertEquals(3, members.size());
        assertEquals("OWNER", roleOf(members.get(0), A), "建群的人要在成员列表里并且是 OWNER");

        assertOk(postJson(CONV + "/member/role", A,
                json(map("conversationId", gc, "userId", B, "role", "ADMIN"))));
        ResponseEntity<String> afterRole = postJson(CONV + "/members", A, json(map("conversationId", gc)));
        Map<?, ?> bRow = firstMember(dataOf(afterRole), B);
        assertEquals("ADMIN", bRow.get("role"), "改角色要真的落库");

        assertOk(postJson(CONV + "/member/add", A, json(map("conversationId", gc,
                "userIds", Collections.singletonList(OUTSIDER_2)))));
        assertEquals(4, listSize(postJson(CONV + "/members", A, json(map("conversationId", gc)))),
                "加人之后 4 个");
        ResponseEntity<String> removed = postJson(CONV + "/member/remove", A,
                json(map("conversationId", gc, "userIds", Collections.singletonList(OUTSIDER_2))));
        assertOk(removed);
        assertEquals(1L, longOf(dataOf(removed)), "返回真被删掉的行数");
        assertEquals(3, listSize(postJson(CONV + "/members", A, json(map("conversationId", gc)))));

        // 3. 发言 / 幂等 / @ 与引用 / 增量历史
        Map<String, Object> m1 = dataMap(postJson(MSG + "/send", A,
                json(map("conversationId", gc, "content", "第一条", "clientMsgId", "rest-cm-1"))));
        long firstSeq = longOf(m1.get("seq"));
        assertEquals(1L, firstSeq);
        Map<String, Object> m2 = dataMap(postJson(MSG + "/send", B, json(map(
                "conversationId", gc, "content", "第二条", "atUserIds", Arrays.asList(OUTSIDER, A),
                "replyToSeq", firstSeq))));
        assertEquals(2L, longOf(m2.get("seq")));
        assertEquals(A + "," + OUTSIDER, m2.get("atUserIds"));
        assertEquals(firstSeq, longOf(m2.get("replyToSeq")));

        Map<String, Object> retry = dataMap(postJson(MSG + "/send", A,
                json(map("conversationId", gc, "content", "第一条", "clientMsgId", "rest-cm-1"))));
        assertEquals(m1.get("id"), retry.get("id"), "弱网重发只能有一个气泡");
        assertEquals(firstSeq, longOf(retry.get("seq")));
        assertEquals(2, listSize(postJson(MSG + "/history", A,
                json(map("conversationId", gc, "sinceSeq", 0, "size", 10)))), "幂等重发不多插一行");
        assertEquals(2L, longOf(dataOf(postJson(MSG + "/head", A, json(map("conversationId", gc))))));

        List<Object> tail = dataOf(postJson(MSG + "/history", A,
                json(map("conversationId", gc, "sinceSeq", 1, "size", 10))));
        assertEquals(1, tail.size(), "增量拉取只该拿到 seq>1 的那一条");
        assertEquals(2L, longOf(((Map<?, ?>) tail.get(0)).get("seq")));
        assertEquals(1, listSize(postJson(MSG + "/history", A,
                json(map("conversationId", gc, "sinceSeq", 0, "size", 1)))), "size 上限要真落到 SQL 上");

        // 4. 已读与未读
        assertEquals(2L, longOf(dataOf(postJson(READ + "/unread", B, json(map("conversationId", gc))))));
        Map<String, Object> mark = dataMap(postJson(READ + "/mark", B,
                json(map("conversationId", gc, "lastReadSeq", 1))));
        assertEquals(1L, longOf(mark.get("lastReadSeq")));
        assertEquals(1L, longOf(mark.get("unreadCount")));
        // 猎物：迟到的旧值盖不动游标
        Map<String, Object> stale = dataMap(postJson(READ + "/mark", B,
                json(map("conversationId", gc, "lastReadSeq", 1))));
        assertEquals(1L, longOf(stale.get("lastReadSeq")));
        // 不传 lastReadSeq = 读到最新
        Map<String, Object> toHead = dataMap(postJson(READ + "/mark", B, json(map("conversationId", gc))));
        assertEquals(2L, longOf(toHead.get("lastReadSeq")));
        assertEquals(0L, longOf(toHead.get("unreadCount")));

        List<Object> receipts = dataOf(postJson(READ + "/receipts", A, json(map("conversationId", gc))));
        assertEquals(1, receipts.size(), "只有 B 标过已读");
        assertEquals(B, longOf(((Map<?, ?>) receipts.get(0)).get("userId")));
        assertEquals(2L, longOf(((Map<?, ?>) receipts.get(0)).get("lastReadSeq")));

        Map<String, Object> summary = dataMap(postJson(READ + "/summary", A, "{}"));
        assertEquals(2L, longOf(summary.get("conversationCount")), "A 在两个会话里");
        assertEquals(2L, longOf(summary.get("total")), "A 一条没读：群里自己发的 2 条也算未读，单聊 0 条");

        // 5. 免打扰与清空
        assertOk(postJson(CONV + "/mute", A, json(map("conversationId", gc, "muted", true))));
        long cleared = longOf(dataOf(postJson(CONV + "/clear", A, json(map("conversationId", gc)))));
        assertEquals(2L, cleared);
        assertEquals(0, listSize(postJson(MSG + "/history", A, json(map("conversationId", gc, "sinceSeq", 0)))),
                "清空之后自己再也拉不到旧消息");
        assertEquals(2, listSize(postJson(MSG + "/history", B, json(map("conversationId", gc, "sinceSeq", 0)))),
                "对照：B 的历史一条不少（清空只是自己的可见游标）");
        ResponseEntity<String> gone = postJson(MSG + "/at", A, json(map("conversationId", gc, "seq", 1)));
        assertEquals(404, gone.getStatusCodeValue(), "清空区间内的消息 404: " + gone.getBody());

        long afterClear = longOf(dataMap(postJson(MSG + "/send", A,
                json(map("conversationId", gc, "content", "清空之后")))).get("seq"));
        assertEquals(3L, afterClear);
        assertEquals(1, listSize(postJson(MSG + "/history", A, json(map("conversationId", gc, "sinceSeq", 0)))));
        assertOk(postJson(MSG + "/at", A, json(map("conversationId", gc, "seq", afterClear))),
                "对照：清空之后的消息查得到");
    }

    @Test
    public void conversationListPagesWithOneBasedPageAndSize() {
        Long c1 = conversationService.single(A, B, null).getId();
        Long c2 = conversationService.createGroup(A, Collections.singletonList(B),
                "分页", null, null, "GROUP").getId();
        messageService.send(c1, B, "单聊的一条");
        messageService.send(c2, B, "群里的一条");

        Map<String, Object> first = dataMap(postJson(CONV + "/list", A, json(map("page", 1, "size", 1))));
        List<Object> records = (List<Object>) first.get("records");
        assertEquals(1, records.size(), "size=1 只该给一条");
        assertEquals(2L, longOf(first.get("total")), "total 是总数，不是本页条数");
        assertEquals(c2, longOf(((Map<?, ?>) records.get(0)).get("conversationId")), "最新的在前");
        assertNotNull(((Map<?, ?>) records.get(0)).get("lastMsgPreview"));

        Map<String, Object> second = dataMap(postJson(CONV + "/list", A, json(map("page", 2, "size", 1))));
        List<Object> secondRecords = (List<Object>) second.get("records");
        assertEquals(1, secondRecords.size());
        assertEquals(c1, longOf(((Map<?, ?>) secondRecords.get(0)).get("conversationId")),
                "page 是 1 起的：第二页拿到另一条");
    }

    // ---------------------------------------------------------------- 错误形状

    @Test
    public void forbiddenAndNotFoundAndBadRequestEachKeepTheirOwnCode() {
        Long conv = conversationService.createGroup(A, Collections.singletonList(B),
                "错误形状", null, null, "GROUP").getId();
        String body = json(map("conversationId", conv));

        // 403：不是成员。web 层的兜底 advice 会把没排上序的异常吞成 500，这几条就是钉住排序的。
        for (String path : Arrays.asList(CONV + "/members", CONV + "/clear", CONV + "/mute",
                MSG + "/send", MSG + "/history", MSG + "/head", MSG + "/at",
                READ + "/mark", READ + "/unread", READ + "/receipts")) {
            ResponseEntity<String> resp = postJson(path, OUTSIDER, body);
            assertEquals(403, resp.getStatusCodeValue(), path + " 越权应当 403: " + resp.getBody());
            assertEquals(403, codeOf(resp), path + " 的 code 应当是 403: " + resp.getBody());
        }
        // 对照：成员同一条调用成功
        for (String path : Arrays.asList(CONV + "/members", MSG + "/history", MSG + "/head",
                READ + "/unread", READ + "/receipts", READ + "/mark")) {
            ResponseEntity<String> resp = postJson(path, A, body);
            assertEquals(200, codeOf(resp), path + " 成员应当 200: " + resp.getBody());
        }

        // 404 只留给"成员闸门放行之后确实读不到东西"：成员行还在、会话行没了（我建过但被删了）。
        String nope = json(map("conversationId", 987654321L));
        Long doomed = conversationService.createGroup(A, Collections.singletonList(B),
                "被删掉的会话", null, null, "GROUP").getId();
        assertEquals(1, conversationMapper.deleteById(doomed), "前置：只删会话行，成员行留着");
        ResponseEntity<String> gone = postJson(CONV + "/clear", A, json(map("conversationId", doomed)));
        assertEquals(404, gone.getStatusCodeValue(), "成员还在而会话行没了要 404: " + gone.getBody());
        assertEquals(404, codeOf(postJson(MSG + "/head", A, json(map("conversationId", doomed)))));
        // 一个从来不存在的 id：只能 403。回 404 就等于把错误码做成枚举会话 id 的 oracle，
        // 那与本模块 notMember 的口径相反（原来这两条断言写的正是 404）。
        ResponseEntity<String> probe = postJson(CONV + "/clear", A, nope);
        assertEquals(403, probe.getStatusCodeValue(), "探测不存在的 id 只该拿到 403: " + probe.getBody());
        assertEquals(403, codeOf(postJson(MSG + "/head", A, nope)));
        // 对照：还活着的会话同一条调用 200
        assertEquals(200, codeOf(postJson(MSG + "/head", A, body)));

        // 400：缺参数 / 非法取值 / 坏 JSON —— 都不许被兜底成 500
        assertEquals(400, postJson(CONV + "/single", A, json(map("userId", B))).getStatusCodeValue(),
                "单聊少了 peerUserId 要 400");
        assertEquals(400, codeOf(postJson(MSG + "/send", A, json(map("conversationId", conv)))),
                "少了 content 要 400");
        assertEquals(400, codeOf(postJson(MSG + "/send", A,
                json(map("conversationId", conv, "msgType", "UNKNOWN", "content", "x")))));
        assertEquals(400, codeOf(postJson(MSG + "/send", A,
                json(map("conversationId", conv, "msgType", "SYS", "content", "冒充系统")))));
        assertEquals(400, codeOf(postJson(CONV + "/group", A,
                json(map("title", "坏类型", "convType", "NOT_A_TYPE")))));
        assertEquals(400, codeOf(postJson(CONV + "/member/role", A,
                json(map("conversationId", conv, "userId", B, "role", "KING")))));
        assertEquals(400, codeOf(postJson(CONV + "/single", A, json(map("peerUserId", A)))),
                "不能和自己开单聊");
        assertEquals(400, codeOf(postJson(CONV + "/list", A, "这不是 JSON")), "坏 JSON 要 400 不要 500");
        // 对照：同一个 endpoint 换合法入参就要成功
        assertEquals(200, codeOf(postJson(MSG + "/send", A,
                json(map("conversationId", conv, "content", "合法的一条")))));
    }

    // ---------------------------------------------------------------- 工具

    /** 未登录 / 已登录两轮都要跑的整面清单。值里的 {@code __CONV__} 会被换成真 id。 */
    private static Map<String, String> allEndpoints() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put(CONV + "/list", json(map("page", 1, "size", 10)));
        m.put(CONV + "/single", json(map("peerUserId", B)));
        m.put(CONV + "/group", json(map("title", "HTTP 群", "memberUserIds", Collections.singletonList(B))));
        m.put(CONV + "/members", json(map("conversationId", "__CONV__")));
        m.put(CONV + "/member/add", json(map("conversationId", "__CONV__",
                "userIds", Collections.singletonList(OUTSIDER_2))));
        m.put(CONV + "/member/remove", json(map("conversationId", "__CONV__",
                "userIds", Collections.singletonList(OUTSIDER_2))));
        // role=OWNER 是"转让"：这一条之后 A 降成 ADMIN（还是 manager，后面的 /member/* 不受影响），
        // 而最后那条 /leave 才走得通 —— owner 不能直接退群，那是 setRole 的硬规则。
        m.put(CONV + "/member/role", json(map("conversationId", "__CONV__", "userId", B, "role", "OWNER")));
        m.put(CONV + "/mute", json(map("conversationId", "__CONV__", "muted", true)));
        m.put(CONV + "/clear", json(map("conversationId", "__CONV__")));
        m.put(MSG + "/send", json(map("conversationId", "__CONV__", "content", "HTTP 发的一条")));
        m.put(MSG + "/history", json(map("conversationId", "__CONV__", "sinceSeq", 0)));
        m.put(MSG + "/at", json(map("conversationId", "__CONV__", "seq", 1)));
        m.put(MSG + "/head", json(map("conversationId", "__CONV__")));
        m.put(READ + "/mark", json(map("conversationId", "__CONV__")));
        m.put(READ + "/unread", json(map("conversationId", "__CONV__")));
        m.put(READ + "/summary", "{}");
        m.put(READ + "/receipts", json(map("conversationId", "__CONV__")));
        // leave 放最后：它会把调用者从会话里摘掉，放前面后面的断言就都失效
        m.put(CONV + "/leave", json(map("conversationId", "__CONV__")));
        return m;
    }

    private int listSize(ResponseEntity<String> resp) {
        List<Object> rows = dataOf(resp);
        assertOk(resp);
        return rows.size();
    }

    private static Map<?, ?> firstMember(List<Object> rows, Long userId) {
        for (Object o : rows) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (userId.equals(Long.valueOf(longOf(m.get("userId"))))) {
                return m;
            }
        }
        throw new AssertionError("成员列表里没有 user=" + userId + ": " + rows);
    }

    private static String roleOf(Object memberRow, Long userId) {
        Map<?, ?> m = (Map<?, ?>) memberRow;
        assertEquals(userId, Long.valueOf(longOf(m.get("userId"))), "这一行不是要找的人: " + m);
        return String.valueOf(m.get("role"));
    }
}
