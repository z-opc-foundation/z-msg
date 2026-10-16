package com.zifang.z.msg.im.api;

import com.zifang.z.msg.im.ImSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「Long 一律出字符串」这条线上的依赖。
 *
 * <p>背景：本模块实体上原来逐字段挂着 {@code @JsonSerialize(using = ToStringSerializer.class)}，
 * 2026-10-16 全部删除——因为 {@code z-boot-web-starter} 的 {@code GlobalJacksonConfig}
 * 已经通过 {@code spring.factories} 的 {@code EnableAutoConfiguration} 登记在类路径上，
 * 把<b>所有</b> Long（含 MyBatis-Plus {@code IPage} 的 total/size/current/pages）都出成字符串。
 * 那 15 处注解是冗余，不是承重；删前删后打真实 HTTP 逐字段比对过，形状一字未变。
 *
 * <p>所以现在这条契约的承重方是 starter 版本，不是本仓代码。这里把它钉住：
 * 万一有人把 {@code z-boot-web-starter} 降到不含该配置的版本，
 * 雪花 id 会悄悄变回数字，前端 {@code JSON.parse} 之后末位被舍入，
 * 现象是「刚建好的会话立刻 403 无权访问」——和权限 bug 一模一样，从头查错方向。
 */
public class ImLongShapeTest extends ImSpringTestSupport {

    private static final Long A = 9101L;
    private static final Long B = 9102L;

    private static final String CONV = "/api/msg/im/conversation";
    private static final String MSG = "/api/msg/im/message";

    private String openConversation() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("peerUserId", B);
        ResponseEntity<String> resp = postJson(CONV + "/single", A, json(body));
        Object id = dataMap(resp).get("id");
        assertTrue(id instanceof String, "会话 id 必须是字符串: " + resp.getBody());
        return (String) id;
    }

    @Test
    public void everyLongGoesOutAsStringIncludingTheOnesNoFieldEverAnnotated() {
        String convId = openConversation();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conversationId", convId);
        body.put("content", "Long 形状");
        Map<String, Object> sent = dataMap(postJson(MSG + "/send", A, json(body)));

        // 曾经逐字段标过注解的
        for (String k : new String[]{"id", "conversationId", "senderUserId"}) {
            assertTrue(sent.get(k) instanceof String, k + " 应出字符串: " + sent);
        }
        // 从来没标过注解的 —— 全靠 GlobalJacksonConfig
        assertTrue(sent.get("seq") instanceof String, "seq 应出字符串: " + sent);
        assertEquals("1", String.valueOf(sent.get("seq")));

        // 非 Long 的数值类型不受影响：Integer 还是数字
        assertTrue(sent.get("memberCount") == null, "本用例不发列表, 只看 seq 这类 Long");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void pagedListTotalsAreStringsToo() {
        openConversation();

        Map<String, Object> data = dataMap(postJson(CONV + "/list", A, json(map("page", 1, "size", 10))));
        for (String k : new String[]{"total", "size", "current", "pages"}) {
            assertTrue(data.get(k) instanceof String, k + " 应出字符串: " + data);
        }

        List<Object> records = (List<Object>) data.get("records");
        assertTrue(records instanceof List, "records 应出数组: " + data);
        Map<String, Object> first = (Map<String, Object>) records.get(0);
        // 列表里也有从来没标过注解的 Long：水位线
        for (String k : new String[]{"lastMsgSeq", "myLastReadSeq", "myClearedSeq", "unreadCount"}) {
            assertTrue(first.get(k) instanceof String, k + " 应出字符串: " + first);
        }
        // Integer 不受影响
        assertTrue(first.get("memberCount") instanceof Number && !(first.get("memberCount") instanceof String),
                "memberCount 是 Integer，仍出数字: " + first);
        assertTrue(first.get("muted") instanceof Number && !(first.get("muted") instanceof String),
                "muted 是 Integer，仍出数字: " + first);
    }
}
