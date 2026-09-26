package com.zifang.z.msg.im.domain.service;

import com.zifang.z.msg.im.ImSpringTestSupport;
import com.zifang.z.msg.im.domain.entity.ImMemberDO;
import com.zifang.z.msg.im.domain.entity.ImReadReceiptDO;
import com.zifang.z.msg.im.domain.mapper.ImConversationMapper;
import com.zifang.z.msg.im.domain.mapper.ImMemberMapper;
import com.zifang.z.msg.im.domain.model.ImConvTypes;
import com.zifang.z.msg.im.domain.model.ImForbiddenException;
import com.zifang.z.msg.im.domain.model.ImNotFoundException;
import com.zifang.z.msg.im.domain.model.ImUnread;
import org.junit.jupiter.api.Test;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 已读与未读。
 * <p>
 * 已读游标的敌人是"迟到的旧值"：弱网重传、多端各写各的、客户端本地时钟不准。
 * 所以每条用例都拿一个<b>乱序旧值当猎物</b>，断言它盖不动新值。
 * 单调性由 SQL 谓词 {@code AND last_read_seq < #{seq}} 保证，
 * 这里也顺手把"负数不出现"这条口径钉住。
 */
public class ImReadServiceTest extends ImSpringTestSupport {

    @Resource
    private ImMemberMapper memberMapper;
    @Resource
    private ImConversationMapper conversationMapper;

    @Test
    public void markReadAdvancesAndStaleValueCannotRewindIt() {
        Long conv = conversationService.single(A, B, null).getId();
        for (int i = 1; i <= 3; i++) {
            messageService.send(conv, A, "消息 " + i);
        }
        assertEquals(3L, readService.unread(conv, A), "前置：一条没读，未读 = 水位");

        assertEquals(2L, readService.markRead(conv, A, 2L));
        assertEquals(1L, readService.unread(conv, A));
        assertEquals(2L, readService.currentReadSeq(conv, A));

        // 猎物：迟到的旧回执（重传 / 另一端的落后游标）
        assertEquals(2L, readService.markRead(conv, A, 1L), "旧值不能把游标拉回去");
        assertEquals(1L, readService.unread(conv, A), "旧回执也不该改变未读数");
        assertEquals(2L, readService.markRead(conv, A, -7L), "负数按 0 处理：不报错，也不动已经前进的游标");

        // 对照：合法的新值照样推进
        assertEquals(3L, readService.markRead(conv, A, 3L));
        assertEquals(0L, readService.unread(conv, A));

        // 谎报未来：以会话水位封顶，不能把还没发的消息算成已读
        assertEquals(3L, readService.markRead(conv, A, 9_999L));
        messageService.send(conv, B, "标完之后又来一条");
        assertEquals(1L, readService.unread(conv, A), "水位抬了，未读要重新出现");
    }

    @Test
    public void receiptsAreOneRowPerPersonAndVisibleToOtherMembers() {
        Long conv = conversationService.createGroup(A, Arrays.asList(B, OUTSIDER),
                "回执可见性", null, null, ImConvTypes.GROUP).getId();
        messageService.send(conv, A, "一");
        messageService.send(conv, A, "二");

        readService.markRead(conv, B, 1L);
        readService.markRead(conv, B, 2L);
        // 猎物：B 的另一端把落后的 1 又报一次
        readService.markRead(conv, B, 1L);

        List<ImReadReceiptDO> rows = readService.receipts(conv, A);
        assertEquals(1, countFor(rows, B), "同一 (会话, 人) 只能有一行回执");
        assertEquals(2L, valueFor(rows, B), "回执游标取到的必须是最高那次");
        assertTrue(rows.size() >= 1, "A 作为成员看得见回执: " + describeReceipts(rows));

        // 对照：OUTSIDER 标了之后，A 读到的是两行，谁也别想冒充谁
        messageService.send(conv, A, "三");
        readService.markRead(conv, OUTSIDER, 3L);
        List<ImReadReceiptDO> after = readService.receipts(conv, A);
        assertEquals(2, after.size(), "每人一行: " + describeReceipts(after));
        assertEquals(3L, valueFor(after, OUTSIDER));
        assertEquals(2L, valueFor(after, B), "别人的回执不能被这一次标已读改写");
    }

    @Test
    public void unreadSummaryAddsUpPerConversationAndFloorsAtZero() {
        Long dm = conversationService.single(A, B, null).getId();
        Long group = conversationService.createGroup(A, Arrays.asList(B, OUTSIDER),
                "汇总群", null, null, ImConvTypes.GROUP).getId();
        messageService.send(dm, B, "单聊一条");
        messageService.send(group, B, "群里一条");
        messageService.send(group, OUTSIDER, "群里两条");

        Map<String, Object> summary = readService.unreadSummary(A);
        assertEquals(3L, longOf(summary.get("total")), "总数 = 各会话未读之和");
        assertEquals(2L, longOf(summary.get("conversationCount")));
        @SuppressWarnings("unchecked")
        List<ImUnread> items = (List<ImUnread>) summary.get("items");
        assertEquals(2, items.size());
        long sum = 0L;
        for (ImUnread u : items) {
            sum += u.getUnreadCount();
            assertNotNull(u.getConversationId());
            assertNotNull(u.getConvType());
        }
        assertEquals(3L, sum);

        // 对照：标完单聊，总数掉到 2，明细里那一栏归零
        readService.markRead(dm, A, 1L);
        Map<String, Object> after = readService.unreadSummary(A);
        assertEquals(2L, longOf(after.get("total")));
        @SuppressWarnings("unchecked")
        List<ImUnread> afterItems = (List<ImUnread>) after.get("items");
        assertEquals(unreadOf(afterItems, dm), 0L);
        assertEquals(unreadOf(afterItems, group), 2L);

        // 下限 0：直接把成员游标写到超过水位（只有绕过服务的写入能做到），未读不能变负
        assertEquals(1, memberMapper.advanceReadSeq(group, A, 99L));
        assertEquals(0L, readService.unread(group, A), "未读口径的下限是 0，不是负数");
        assertEquals(0L, unreadOf((List<ImUnread>) readService.unreadSummary(A).get("items"), group));
    }

    @Test
    public void clearedConversationStaysAtZeroUnreadEvenWithOlderReadCursor() {
        Long conv = conversationService.single(A, B, null).getId();
        messageService.send(conv, B, "旧一");
        messageService.send(conv, B, "旧二");
        readService.markRead(conv, A, 1L);
        assertEquals(1L, readService.unread(conv, A), "前置：读到 1，还剩 1");

        messageService.clear(conv, A);
        assertEquals(0L, readService.unread(conv, A),
                "清空之后不能把清掉那段又算成未读 —— 口径取 max(已读, 清空)");
        ImMemberDO member = conversationService.findMember(conv, A);
        assertEquals(Long.valueOf(1L), member.getLastReadSeq(), "清空不该顺手改已读游标");
        assertEquals(Long.valueOf(2L), member.getClearedSeq());

        // 对照：清空之后新消息照常计未读
        messageService.send(conv, B, "清空之后的新消息");
        assertEquals(1L, readService.unread(conv, A));
    }

    @Test
    public void readStateIsGatedByMembership() {
        Long conv = conversationService.createGroup(A, Collections.singletonList(B),
                "已读闸门", null, null, ImConvTypes.GROUP).getId();
        assertThrows(ImForbiddenException.class, () -> readService.markRead(conv, OUTSIDER, 1L));
        assertThrows(ImForbiddenException.class, () -> readService.unread(conv, OUTSIDER));
        assertThrows(ImForbiddenException.class, () -> readService.receipts(conv, OUTSIDER));
        assertThrows(ImForbiddenException.class, () -> readService.currentReadSeq(conv, OUTSIDER));
        // 一个从来不存在 / 从来不属于我的 id 也只能拿 403：闸门必须先判成员。
        // 这里若回 404，403-vs-404 就成了枚举会话 id 的 oracle（见 notMember 的注释）。
        assertThrows(ImForbiddenException.class, () -> readService.markRead(987654321L, A, 1L));
        assertThrows(ImForbiddenException.class, () -> readService.unread(987654321L, A));
        // 对照：404 留给"成员行还在、会话行没了"——我建过但被删了，这种真读不到不能被 403 吞掉
        Long doomed = conversationService.single(A, B, null).getId();
        assertEquals(1, conversationMapper.deleteById(doomed), "前置：只删会话行，成员行留着");
        assertNotNull(conversationService.findMember(doomed, A), "前置：A 的成员行还在，闸门会放行");
        assertThrows(ImNotFoundException.class, () -> readService.markRead(doomed, A, 1L));
        assertThrows(ImNotFoundException.class, () -> readService.unread(doomed, A));

        // 对照：成员同一条调用都通
        messageService.send(conv, A, "成员才有得读");
        assertEquals(1L, readService.markRead(conv, B, 1L));
        assertEquals(1L, readService.currentReadSeq(conv, B));
        assertEquals(1, readService.receipts(conv, A).size());
        assertTrue(readService.unreadSummary(OUTSIDER).get("items") instanceof List,
                "路人也有自己的汇总，只是空的");
        assertEquals(0L, longOf(readService.unreadSummary(OUTSIDER).get("total")));
    }

    // ---------------------------------------------------------------- 工具

    private static long unreadOf(List<ImUnread> items, Long conv) {
        for (ImUnread u : items) {
            if (conv.equals(u.getConversationId())) {
                return u.getUnreadCount();
            }
        }
        throw new AssertionError("汇总里没有会话 " + conv + "，实际 " + items.size() + " 条");
    }

    private static int countFor(List<ImReadReceiptDO> rows, Long userId) {
        int n = 0;
        for (ImReadReceiptDO r : rows) {
            if (userId.equals(r.getUserId())) {
                n++;
            }
        }
        return n;
    }

    private static long valueFor(List<ImReadReceiptDO> rows, Long userId) {
        for (ImReadReceiptDO r : rows) {
            if (userId.equals(r.getUserId())) {
                return r.getLastReadSeq().longValue();
            }
        }
        throw new AssertionError("回执里没有 user=" + userId);
    }

    private static String describeReceipts(List<ImReadReceiptDO> rows) {
        StringBuilder sb = new StringBuilder();
        for (ImReadReceiptDO r : rows) {
            sb.append('{').append(r.getUserId()).append(':').append(r.getLastReadSeq()).append('}');
        }
        return sb.toString();
    }
}
