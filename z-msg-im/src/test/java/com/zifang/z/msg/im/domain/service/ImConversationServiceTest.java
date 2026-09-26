package com.zifang.z.msg.im.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zifang.z.msg.im.ImSpringTestSupport;
import com.zifang.z.msg.im.domain.entity.ImConversationDO;
import com.zifang.z.msg.im.domain.entity.ImMemberDO;
import com.zifang.z.msg.im.domain.mapper.ImConversationMapper;
import com.zifang.z.msg.im.domain.model.ImConvTypes;
import com.zifang.z.msg.im.domain.model.ImConversationView;
import com.zifang.z.msg.im.domain.model.ImForbiddenException;
import com.zifang.z.msg.im.domain.model.ImNotFoundException;
import com.zifang.z.msg.im.domain.model.ImRoles;
import org.junit.jupiter.api.Test;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话与成员：单聊 create-or-get、建群、成员/角色管理、我的会话列表。
 * <p>
 * 每条"应当被拒"的用例都在同一条用例里配了"换个合法入参就要成功"的对照，
 * 否则"权限判定永远返回 false"也能让整类绿。
 */
public class ImConversationServiceTest extends ImSpringTestSupport {

    @Resource
    private ImConversationMapper conversationMapper;

    // ---------------------------------------------------------------- 单聊

    @Test
    public void singleConversationIsCreateOrGetAndNeverDuplicated() {
        ImConversationDO first = conversationService.single(A, B, "t1");
        assertEquals(ImConvTypes.SINGLE, first.getConvType());
        assertEquals(ImRoles.singlePairKey(A, B), first.getUkPair(),
                "uk_pair 必须是与方向无关的稳定键，否则两个人各建一个会话");

        // 反向再调一次（对端也点了"发消息"）：必须是同一条会话，不多一行
        ImConversationDO again = conversationService.single(B, A, "t1");
        assertEquals(first.getId(), again.getId(), "单聊是 create-or-get，不是每次都新建");
        assertEquals(1, countConversations(), "uk_pair 上不该出现第二条");
        assertEquals(Integer.valueOf(2), again.getMemberCount(),
                "member_count 是会话列表要展示的数字，必须和成员行数一致");
        assertNotNull(conversationService.findMember(first.getId(), A));
        assertNotNull(conversationService.findMember(first.getId(), B));
    }

    @Test
    public void concurrentSingleCreationCollidesOnUkPairAndReReadsInsteadOfThrowing() throws Exception {
        // 8 个线程两个方向同时"开单聊"：撞 uk_pair 的那几个必须把赢家的行读回来，而不是把
        // DuplicateKeyException 甩给调用方——那是弱网重试最常见的形状。
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Long>> futures = new ArrayList<Future<Long>>();
        try {
            for (int i = 0; i < 8; i++) {
                final boolean swap = i % 2 == 0;
                futures.add(pool.submit(new Callable<Long>() {
                    @Override
                    public Long call() {
                        return swap ? conversationService.single(B, A, null).getId()
                                : conversationService.single(A, B, null).getId();
                    }
                }));
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发建会话的线程池应当在期限内跑完");
        }
        List<Long> ids = new ArrayList<Long>();
        for (Future<Long> f : futures) {
            ids.add(f.get());
        }
        assertEquals(1, distinct(ids).size(), "并发建会话只能有一条：" + ids);
        assertEquals(1, countConversations(), "uk_pair 冲突后重读，不能留下脏行");
        ImConversationDO conv = conversationService.requireConversation(ids.get(0));
        assertEquals(Integer.valueOf(2), conv.getMemberCount());
    }

    @Test
    public void selfConversationAndMissingIdentityAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> conversationService.single(A, A, null),
                "本模块不提供自会话（那是\"文件传输助手\"，需要另设语义）");
        assertThrows(IllegalArgumentException.class, () -> conversationService.single(A, null, null));
    }

    // ---------------------------------------------------------------- 群聊 / 聊天室

    @Test
    public void groupCreationMakesOwnerAMemberAndCountsEveryoneOnce() {
        ImConversationDO group = conversationService.createGroup(A,
                Arrays.asList(B, OUTSIDER, B, A), "测试群", null, "t1", ImConvTypes.GROUP);
        assertEquals(ImConvTypes.GROUP, group.getConvType());
        assertEquals("测试群", group.getTitle());
        assertEquals(Integer.valueOf(3), group.getMemberCount(), "重复的人只算一个");

        ImMemberDO ownerRow = conversationService.findMember(group.getId(), A);
        assertNotNull(ownerRow, "owner 必须在成员表里，否则群里没人能管人");
        assertEquals(ImRoles.OWNER, ownerRow.getRole());
        assertEquals(ImRoles.MEMBER, conversationService.findMember(group.getId(), B).getRole());
        assertEquals(3, conversationService.members(A, group.getId()).size());
    }

    @Test
    public void roomTypeIsAcceptedWhileSingleTypeIsRejectedForGroupCreation() {
        ImConversationDO room = conversationService.createGroup(A, Collections.singletonList(B),
                "聊天室", null, null, ImConvTypes.ROOM);
        assertEquals(ImConvTypes.ROOM, room.getConvType());
        assertNull(room.getUkPair(), "只有单聊有 uk_pair；聊天室不能占住这个唯一索引");
        // 对照：同一个方法传 SINGLE 必须被挡回来，而不是悄悄建成单聊
        assertThrows(IllegalArgumentException.class, () -> conversationService.createGroup(
                A, Collections.singletonList(B), "x", null, null, ImConvTypes.SINGLE));
        assertThrows(IllegalArgumentException.class, () -> conversationService.createGroup(
                A, Collections.singletonList(B), "x", null, null, "NOT_A_TYPE"));
    }

    @Test
    public void memberManagementRejectsStrangersButManagersSucceed() {
        Long conv = conversationService.createGroup(A, Arrays.asList(B, OUTSIDER),
                "成员管理群", null, null, ImConvTypes.GROUP).getId();

        // 负：非成员既加不了人也踢不了人
        assertThrows(ImForbiddenException.class,
                () -> conversationService.addMembers(OUTSIDER_2, conv, Collections.singletonList(9999L)));
        assertThrows(ImForbiddenException.class,
                () -> conversationService.removeMembers(OUTSIDER_2, conv, Collections.singletonList(B)));
        assertThrows(ImForbiddenException.class, () -> conversationService.members(OUTSIDER_2, conv),
                "成员列表本身就是社交信息，不能让外人拿会话 id 来枚举");

        // 正（对照）：owner 加人 → 重复加同一个人幂等
        conversationService.addMembers(A, conv, Collections.singletonList(9999L));
        assertTrue(conversationService.isMember(conv, 9999L));
        conversationService.addMembers(A, conv, Collections.singletonList(9999L));
        assertEquals(Integer.valueOf(4), conversationService.requireConversation(conv).getMemberCount(),
                "重复加人不该把 member_count 抬成 5");

        // 正：普通成员不能管人（这是角色闸，不是成员闸）
        assertThrows(ImForbiddenException.class,
                () -> conversationService.addMembers(B, conv, Collections.singletonList(8888L)));

        // 正（对照）：升成 ADMIN 之后同一条调用就要成功
        conversationService.setRole(A, conv, B, ImRoles.ADMIN);
        conversationService.addMembers(B, conv, Collections.singletonList(8888L));
        assertTrue(conversationService.isMember(conv, 8888L));
    }

    @Test
    public void ownerCannotBeKickedAndOwnerCannotLeaveWithoutTransferring() {
        Long conv = conversationService.createGroup(A, Arrays.asList(B, OUTSIDER),
                "转让群", null, null, ImConvTypes.GROUP).getId();

        assertThrows(IllegalArgumentException.class,
                () -> conversationService.removeMembers(A, conv, Collections.singletonList(A)),
                "OWNER 不能被踢（也不能拿踢人当退群用）");
        assertThrows(IllegalArgumentException.class, () -> conversationService.leave(A, conv),
                "owner 直接退群会留下没有 owner 的群");

        // 对照：先把群主转出去，原来的人就能正常退了
        conversationService.setRole(A, conv, B, ImRoles.OWNER);
        assertTrue(conversationService.leave(A, conv));
        assertFalse(conversationService.isMember(conv, A), "退群要删掉成员行");
        assertEquals(ImRoles.OWNER, conversationService.findMember(conv, B).getRole());
        assertEquals(Integer.valueOf(2), conversationService.requireConversation(conv).getMemberCount(),
                "退群之后 member_count 要跟着重算");
    }

    @Test
    public void roleTransferDemotesTheOldOwnerSoOnlyOneOwnerRemains() {
        Long conv = conversationService.createGroup(A, Arrays.asList(B, OUTSIDER),
                "唯一群主", null, null, ImConvTypes.GROUP).getId();
        assertEquals(1, countByRole(conv, ImRoles.OWNER));

        conversationService.setRole(A, conv, OUTSIDER, ImRoles.OWNER);
        assertEquals(1, countByRole(conv, ImRoles.OWNER), "转让之后仍然只能有一个 OWNER");
        assertEquals(ImRoles.OWNER, conversationService.findMember(conv, OUTSIDER).getRole());
        assertEquals(ImRoles.ADMIN, conversationService.findMember(conv, A).getRole());

        // 对照：摘不掉现有 OWNER，但改成正经角色可以
        assertThrows(IllegalArgumentException.class,
                () -> conversationService.setRole(OUTSIDER, conv, OUTSIDER, ImRoles.MEMBER));
        conversationService.setRole(OUTSIDER, conv, B, ImRoles.MEMBER);
        assertEquals(ImRoles.MEMBER, conversationService.findMember(conv, B).getRole());
    }

    @Test
    public void missingConversationAndForeignConversationAnswerTheSameQuestion() {
        Long conv = conversationService.createGroup(A, Collections.singletonList(B),
                "枚举防护", null, null, ImConvTypes.GROUP).getId();

        ImForbiddenException stranger = assertThrows(ImForbiddenException.class,
                () -> conversationService.requireMember(conv, OUTSIDER));
        ImForbiddenException nope = assertThrows(ImForbiddenException.class,
                () -> conversationService.requireMember(123456789L, OUTSIDER));
        // 两句里必然带上调用方自己给的那个会话 id（回显输入不是 oracle），所以"同一句话"
        // 只能按去掉数字之后的句式来断言；原文逐字相等是把断言写成了永假。
        assertEquals(skeleton(stranger.getMessage()), skeleton(nope.getMessage()),
                "\"不存在\"与\"不是成员\"必须是同一句式，否则拿错误信息就能枚举会话 id");
        assertFalse(stranger.getMessage().contains("不存在"), "403 的文案里不能出现\"不存在\": " + stranger);
        assertFalse(nope.getMessage().contains("不存在"), "同上：不存在也只说\"无权访问\": " + nope);
        // 对照：真成员同一条调用成功
        assertNotNull(conversationService.requireMember(conv, B));

        // 对照二：404 那条路确实会写"不存在"——上面两个 assertFalse 才不是把断言钉死成永真
        ImNotFoundException gone = assertThrows(ImNotFoundException.class,
                () -> conversationService.requireConversation(123456789L));
        assertTrue(gone.getMessage().contains("不存在"), "404 的文案要点明不存在: " + gone.getMessage());
        assertNotNull(conversationService.requireConversation(conv), "对照：存在的会话要读得回来");
    }

    // ---------------------------------------------------------------- 我的会话列表

    @Test
    public void conversationListCarriesLastMessageCursorAndPreviewForMember() {
        Long older = conversationService.single(A, B, null).getId();
        Long newer = conversationService.createGroup(A, Arrays.asList(B, OUTSIDER),
                "群聊列表项", null, null, ImConvTypes.GROUP).getId();
        messageService.send(older, A, "第一条会话的内容");
        messageService.send(older, B, "它的第二条");
        messageService.send(newer, A, "最新一条会话的摘要");

        IPage<ImConversationView> page = conversationService.pageMine(A, 1, 10);
        List<ImConversationView> rows = page.getRecords();
        assertEquals(2, rows.size(), "A 在两条会话里，列表就该有两条: " + describe(rows));
        assertEquals(2L, page.getTotal());

        ImConversationView top = rows.get(0);
        assertEquals(newer, top.getConversationId(), "最后有动静的会话排在最前");
        assertEquals("最新一条会话的摘要", top.getLastMsgPreview());
        assertEquals(Long.valueOf(1L), top.getLastMsgSeq());
        assertEquals(Integer.valueOf(3), top.getMemberCount());
        assertEquals("群聊列表项", top.getTitle());
        assertEquals(ImRoles.OWNER, top.getMyRole());
        assertEquals(1L, top.getUnreadCount(),
                "未读 = 水位 - 我的游标：本模块不按发送者扣减，标已读才会把它清掉");

        ImConversationView second = rows.get(1);
        assertEquals(older, second.getConversationId());
        assertEquals(Long.valueOf(2L), second.getLastMsgSeq());
        assertEquals("它的第二条", second.getLastMsgPreview());
        assertEquals(ImConvTypes.SINGLE, second.getConvType());
        assertEquals(2L, second.getUnreadCount(), "A 一条都没读过，未读 = 水位");

        // 对照：同一份数据在 B 眼里是 B 自己的游标 —— 单聊里 A 也发过一条，所以 B 的未读是 2；
        // 群里只有 A 发过，B 的未读是 1。列表按 B 的成员行走，不会串到别人的会话上。
        List<ImConversationView> bRows = conversationService.pageMine(B, 1, 10).getRecords();
        assertEquals(2, bRows.size(), "B 也在这两条会话里: " + describe(bRows));
        for (ImConversationView v : bRows) {
            long expect = older.equals(v.getConversationId()) ? 2L : 1L;
            assertEquals(expect, v.getUnreadCount(), "B 眼里的未读: " + describe(bRows));
            assertNotNull(v.getMyRole());
        }

        // 分页确实生效：size=1 只给一条，但 total 仍是两条
        IPage<ImConversationView> paged = conversationService.pageMine(A, 2, 1);
        assertEquals(1, paged.getRecords().size());
        assertEquals(2L, paged.getTotal());
        assertEquals(older, paged.getRecords().get(0).getConversationId());
    }

    @Test
    public void muteAndUnmuteStickToTheMemberRow() {
        Long conv = conversationService.single(A, B, null).getId();
        assertTrue(conversationService.mute(A, conv, true));
        assertEquals(Integer.valueOf(1), conversationService.findMember(conv, A).getMuted());
        // 对照：免打扰不影响未读统计（那是"不弹"，不是"不算"）
        messageService.send(conv, B, "免打扰也要计未读");
        assertEquals(1L, readService.unread(conv, A));
        assertTrue(conversationService.mute(A, conv, false));
        assertEquals(Integer.valueOf(0), conversationService.findMember(conv, A).getMuted());
        assertThrows(ImForbiddenException.class, () -> conversationService.mute(OUTSIDER, conv, true));
    }

    // ---------------------------------------------------------------- 工具

    /** 成员表按角色数一数：转让群主要保证 OWNER 永远只有一个。 */
    private int countByRole(Long conv, String role) {
        int n = 0;
        for (ImMemberDO m : conversationService.members(A, conv)) {
            if (role.equals(m.getRole())) {
                n++;
            }
        }
        return n;
    }

    private long countConversations() {
        return conversationMapper.selectCount(new LambdaQueryWrapper<ImConversationDO>());
    }

    private static String describe(List<ImConversationView> rows) {
        StringBuilder sb = new StringBuilder();
        for (ImConversationView v : rows) {
            sb.append('{').append(v.getConversationId()).append(" seq=").append(v.getLastMsgSeq())
                    .append(" unread=").append(v.getUnreadCount()).append(" members=")
                    .append(v.getMemberCount()).append('}');
        }
        return sb.toString();
    }

    private static List<Long> distinct(List<Long> ids) {
        List<Long> out = new ArrayList<Long>();
        for (Long id : ids) {
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** 把文案里的数字全部换成占位符：剩下的就是"句式"。 */
    private static String skeleton(String message) {
        return message == null ? null : message.replaceAll("\\d+", "#");
    }
}
