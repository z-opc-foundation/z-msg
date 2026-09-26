package com.zifang.z.msg.im.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zifang.z.msg.im.ImSpringTestSupport;
import com.zifang.z.msg.im.config.ImProperties;
import com.zifang.z.msg.im.domain.entity.ImConversationDO;
import com.zifang.z.msg.im.domain.entity.ImMessageDO;
import com.zifang.z.msg.im.domain.mapper.ImMessageMapper;
import com.zifang.z.msg.im.domain.model.ImConvTypes;
import com.zifang.z.msg.im.domain.model.ImForbiddenException;
import com.zifang.z.msg.im.domain.model.ImHistoryPage;
import org.junit.jupiter.api.Test;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
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
 * 发消息与增量拉历史。核心是两条契约：
 * <ol>
 *   <li><b>seq 在会话内严格单调、不重号</b>——客户端按 {@code seq > sinceSeq} 做增量同步，
 *       重号会覆盖气泡。这条只能靠真并发证明，所以这里开 16 个线程。
 *       <b>不承诺"无洞"</b>：并发重复提交撞上 {@code uk_im_msg_conv_client} 时，输掉那几个
 *       已经报出去的 seq 只能作废（ImSequencer 的注释把这条写成已知代价），
 *       区间语义下客户端看不见差别；对应的用例是
 *       {@link #fourConcurrentRetriesOfOneClientMsgIdLeaveExactlyOneRow()}。</li>
 *   <li><b>实时下发是增强，不是落库的前置条件</b>——没有任何 socket 订阅时消息照样进库、照样读得回来。</li>
 * </ol>
 */
public class ImMessageServiceTest extends ImSpringTestSupport {

    @Resource
    private ImMessageMapper messageMapper;
    /** 直接占号用：造"水位抬了、行没落"这个生产里就存在的形态，不靠手改 SQL。 */
    @Resource
    private ImSequencer sequencer;
    /** 只为把 {@code max-page-size} 压到一位数来验 hasMore 的来历，用完必须还原（bean 是全上下文共享的）。 */
    @Resource
    private ImProperties properties;

    // ---------------------------------------------------------------- seq

    @Test
    public void sixteenThreadsOnOneConversationProduceContiguousNonRepeatingSeq() throws Exception {
        final Long conv = conversationService.createGroup(A, Arrays.asList(B, OUTSIDER),
                "并发占号", null, null, ImConvTypes.GROUP).getId();
        final int threads = 16;
        final int perThread = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CyclicBarrier ready = new CyclicBarrier(threads);
        List<Future<Long>> futures = new ArrayList<Future<Long>>();
        final List<Long> seqs = Collections.synchronizedList(new ArrayList<Long>());
        try {
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                futures.add(pool.submit(new Callable<Long>() {
                    @Override
                    public Long call() throws Exception {
                        ready.await(30, TimeUnit.SECONDS);
                        long last = -1L;
                        for (int i = 0; i < perThread; i++) {
                            ImMessageDO m = messageService.send(conv, A, "并发消息 " + tid + "-" + i);
                            seqs.add(m.getSeq());
                            last = m.getSeq();
                        }
                        return last;
                    }
                }));
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发发消息应当在期限内跑完");
        }
        for (Future<Long> f : futures) {
            assertNotNull(f.get());
        }

        int total = threads * perThread;
        assertEquals(total, seqs.size(), "每次调用都该返回一条消息");
        Set<Long> unique = new HashSet<Long>(seqs);
        assertEquals(total, unique.size(), "seq 不能重号（客户端会把两个气泡叠成一个）: " + seqs);

        // 库里也数一遍：不能出现"返回了 seq 却没落库"
        List<ImMessageDO> rows = messageMapper.selectList(new LambdaQueryWrapper<ImMessageDO>()
                .eq(ImMessageDO::getConversationId, conv));
        assertEquals(total, rows.size(), "表里的行数必须和返回的消息数一致");
        Set<Long> dbSeqs = new HashSet<Long>();
        for (ImMessageDO r : rows) {
            dbSeqs.add(r.getSeq());
        }
        assertEquals(total, dbSeqs.size(), "表里的 seq 也不能重号");
        for (int i = 1; i <= total; i++) {
            assertTrue(unique.contains((long) i), "seq 序列里缺了 " + i + "（空洞 = 永久丢消息）");
        }
        ImConversationDO head = conversationService.requireConversation(conv);
        assertEquals(Long.valueOf(total), head.getLastMsgSeq(),
                "会话水位必须正好等于发出去的消息条数，多一分就是烧号");
        assertNotNull(head.getLastMsgId());
        assertNotNull(head.getLastMsgPreview());
    }

    @Test
    public void seqStartsAtOneAndIncrementsOnePerMessageInEachConversationSeparately() {
        Long one = conversationService.single(A, B, null).getId();
        Long two = conversationService.single(A, OUTSIDER, null).getId();
        assertEquals(1L, messageService.send(one, A, "一的第一条").getSeq().longValue());
        assertEquals(2L, messageService.send(one, A, "一的第二条").getSeq().longValue());
        assertEquals(1L, messageService.send(two, A, "二的第一条").getSeq().longValue(),
                "seq 是会话内的水位，不是全局的");
        assertEquals(2L, messageService.headSeq(one, A));
        assertEquals(1L, messageService.headSeq(two, A));
    }

    // ---------------------------------------------------------------- 幂等

    @Test
    public void sameClientMsgIdIsOneRowNoMatterHowManyTimesItIsSubmitted() {
        Long conv = conversationService.single(A, B, null).getId();
        ImMessageDO first = messageService.send(conv, A, "TEXT", "弱网重发的同一条", "cmid-1", null, null);
        ImMessageDO retry = messageService.send(conv, A, "TEXT", "弱网重发的同一条", "cmid-1", null, null);
        assertEquals(first.getId(), retry.getId(), "幂等重发必须返回同一个气泡");
        assertEquals(first.getSeq(), retry.getSeq(), "重发不能占新的 seq");
        assertEquals(1, countByClientMsgId(conv, "cmid-1"));
        assertEquals(1L, messageService.headSeq(conv, A), "重发也不该抬会话水位");

        // 对照：换一个 client_msg_id 就要真的插第二条
        ImMessageDO other = messageService.send(conv, A, "TEXT", "另一条", "cmid-2", null, null);
        assertTrue(other.getId().longValue() != first.getId().longValue());
        assertEquals(2L, messageService.headSeq(conv, A));
        // 对照：不传 client_msg_id 就不参与幂等（两条都是真的新消息）
        assertNotNull(messageService.send(conv, A, "TEXT", "无 key 一", null, null, null).getId());
        assertNotNull(messageService.send(conv, A, "TEXT", "无 key 二", null, null, null).getId());
        assertEquals(4L, messageService.headSeq(conv, A));
    }

    @Test
    public void fourConcurrentRetriesOfOneClientMsgIdLeaveExactlyOneRow() throws Exception {
        final Long conv = conversationService.single(A, B, null).getId();
        final int racers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        final CyclicBarrier ready = new CyclicBarrier(racers);
        List<Future<Long>> futures = new ArrayList<Future<Long>>();
        try {
            for (int i = 0; i < racers; i++) {
                futures.add(pool.submit(new Callable<Long>() {
                    @Override
                    public Long call() throws Exception {
                        ready.await(30, TimeUnit.SECONDS);
                        return messageService.send(conv, A, "TEXT", "同时重发", "cmid-race", null, null).getId();
                    }
                }));
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发重发应当跑完");
        }
        Set<Long> ids = new HashSet<Long>();
        for (Future<Long> f : futures) {
            ids.add(f.get());
        }
        assertEquals(1, ids.size(), "四个并发重发必须拿到同一个消息 id: " + ids);
        assertEquals(1, countByClientMsgId(conv, "cmid-race"), "表里只能有一行");
        ImMessageDO winner = messageMapper.selectById(ids.iterator().next());
        assertNotNull(winner, "拿到的 id 必须在库里");
        long head = messageService.headSeq(conv, A);
        // 输掉的那三个是在 CAS 占号<em>之后</em>才撞上唯一索引的，号已经报出去了只能作废 ——
        // ImSequencer 的注释把这条空洞写成已知代价（客户端按 seq>sinceSeq 的区间语义拉取，
        // 洞对它不可见）。所以这里断言不了"水位 == 1"，能钉的是三件事：
        // 水位不落后于真落库的那一条、作废的号不超过并发度、对端只看到一条。
        assertTrue(winner.getSeq().longValue() <= head,
                "水位不能落后于落库的那条: seq=" + winner.getSeq() + " head=" + head);
        assertTrue(head <= racers, "作废的号最多到并发度: head=" + head);
        List<ImMessageDO> peer = messageService.history(conv, B, 0L, 10);
        assertEquals(1, peer.size(), "对端只能补拉回一条: " + seqDump(peer));
        assertEquals(winner.getSeq().longValue(), peer.get(0).getSeq().longValue());

        // 对照一：换一个 client_msg_id 的正常发送只推进一格 —— 作废不会把计数器搞坏
        messageService.send(conv, A, "TEXT", "下一个", "cmid-next", null, null);
        assertEquals(head + 1L, messageService.headSeq(conv, A), "正常发送必须恰好 +1");
        // 对照二：串行重发（没有竞争）走"先查幂等"那条路，一个号都不占
        messageService.send(conv, A, "TEXT", "下一个", "cmid-next", null, null);
        assertEquals(head + 1L, messageService.headSeq(conv, A), "串行重发绝不能占新号");
        assertEquals(1, countByClientMsgId(conv, "cmid-next"), "串行重发也不多插一行");
    }

    // ---------------------------------------------------------------- 历史

    @Test
    public void historyIsAscendingIncrementalAndCappedInSql() {
        Long conv = conversationService.createGroup(A, Arrays.asList(B, OUTSIDER),
                "历史分页", null, null, ImConvTypes.GROUP).getId();
        for (int i = 1; i <= 10; i++) {
            messageService.send(conv, A, "第 " + i + " 条");
        }
        List<ImMessageDO> all = messageService.history(conv, A, 0L, 100);
        assertEquals(10, all.size());
        assertAscendingByOne(all, 1L, 10L);

        List<ImMessageDO> tail = messageService.history(conv, A, 7L, 100);
        assertAscendingByOne(tail, 8L, 10L);

        // 上限必须真落在 LIMIT 上：只给 3 条就只能拿 3 条，而且是水位里最新的区间起点
        List<ImMessageDO> capped = messageService.history(conv, A, 0L, 3);
        assertAscendingByOne(capped, 1L, 3L);

        // size<=0 走 z-msg.im.default-page-size，不是"不给数据"也不是"全给"
        List<ImMessageDO> defaulted = messageService.history(conv, A, 0L, 0);
        assertEquals(10, defaulted.size(), "默认页大小 50 > 10，应当全给");

        // 越界水位：拿不到任何一条，但不报错（客户端拿着未来的 seq 来同步是正常事）
        assertTrue(messageService.history(conv, A, 999L, 10).isEmpty());
        // 负数 sinceSeq 按 0 处理，不是报错
        assertEquals(10, messageService.history(conv, A, -5L, 100).size());
    }

    @Test
    public void hasMoreIsProbedNotGuessedAndTheCursorLoopTerminates() {
        Long conv = conversationService.createGroup(A, Collections.singletonList(B),
                "同步判定量", null, null, ImConvTypes.GROUP).getId();
        for (int i = 1; i <= 10; i++) {
            messageService.send(conv, A, "第 " + i + " 条");
        }
        // 把服务端大写压到 3：这时"请求 500 条"和"库里只剩 3 条"在裸数组上长得一模一样，
        // 而 hasMore 是靠多读一条探出来的，不该跟着客户端给的 size 漂。
        final int saved = properties.getMaxPageSize();
        properties.setMaxPageSize(3);
        try {
            ImHistoryPage first = messageService.historyPage(conv, A, 0L, 500);
            assertEquals(3, first.getRows().size(), "大写要真落在 LIMIT 上，不是给完再切");
            assertTrue(first.isHasMore(), "被夹掉之后仍要说清后面还有：这是 size=500 与 max=3 之间唯一的信号");
            assertEquals(3L, first.getNextSinceSeq());

            // 客户端能照着 nextSinceSeq 走到底：不早停（不丢消息）、不死循环（终会 hasMore=false）
            int seen = first.getRows().size();
            long cursor = first.getNextSinceSeq();
            boolean more = first.isHasMore();
            int pages = 1;
            while (more) {
                ImHistoryPage p = messageService.historyPage(conv, A, cursor, 500);
                assertTrue(p.getRows().size() <= 3, "每一页都不许超过服务端大写: " + p.getRows().size());
                seen += p.getRows().size();
                assertTrue(p.getNextSinceSeq() > cursor, "游标必须严格前进，否则这就是个死循环");
                cursor = p.getNextSinceSeq();
                more = p.isHasMore();
                pages++;
                assertTrue(pages <= 10, "页数不可能超过消息条数：游标没在前进");
            }
            assertEquals(10, seen, "照 nextSinceSeq 翻到底，一条不多一条不少");
            assertEquals(4, pages);

            // 正好回满最后一页时也不许谎报 hasMore（上面循环的最后一步就是这一档）
            ImHistoryPage tail = messageService.historyPage(conv, A, 9L, 500);
            assertEquals(1, tail.getRows().size());
            assertFalse(tail.isHasMore(), "只剩一条时 hasMore 必须收住，否则客户端永远在空转");
            // 满页并且后面真的没有了：这一档专门区分"回满 size"与"还有下一批"
            ImHistoryPage exact = messageService.historyPage(conv, A, 7L, 500);
            assertEquals(3, exact.getRows().size(), "前置：正好回满一页，否则这一条没有猎物");
            assertFalse(exact.isHasMore(), "满页不等于还有：hasMore 只能来自多读的那一条");
            // 空页：游标停在原地向前，不报错也不回退
            ImHistoryPage empty = messageService.historyPage(conv, A, 10L, 500);
            assertTrue(empty.getRows().isEmpty());
            assertFalse(empty.isHasMore());
            assertEquals(10L, empty.getNextSinceSeq());
        } finally {
            properties.setMaxPageSize(saved);
        }
        assertEquals(saved, properties.getMaxPageSize(), "改过共享 bean 必须还原，否则后面的用例在假上限下跑");
    }

    @Test
    public void pageMetadataMakesASeqGapVisibleWithoutGuessingItsCause() {
        Long conv = conversationService.createGroup(A, Collections.singletonList(B),
                "空洞信号", null, null, ImConvTypes.GROUP).getId();
        for (int i = 1; i <= 4; i++) {
            messageService.send(conv, A, "第 " + i + " 条");
        }
        // 造一个真空洞，用的就是生产里那条已知代价的机制本身：占号成功、随后没有落库的行
        // （并发重复提交撞唯一索引时输家就是这个形态）。不靠手改 SQL，也不靠 mock。
        long claimed = sequencer.append(conversationService.requireConversation(conv),
                sequencer.newMessageId(), "占了号但没有消息");
        assertEquals(5L, claimed);

        ImHistoryPage page = messageService.historyPage(conv, A, 0L, 100);
        assertEquals(4, page.getRows().size());
        assertEquals(4L, page.getNextSinceSeq());
        assertFalse(page.isHasMore(), "库里确实没有 seq>4 的行，不该说后面还有");
        assertEquals(5L, page.getHeadSeq(), "水位比拿到的最后一条高 1：空洞就此显形");
        assertEquals(1L, page.getMinVisibleSeq(), "没清空过，可见下界就是 1");
        // 客户端据此能算出"少了 5 号"，而且知道这不是被自己的 cleared_seq 挡掉的：
        // minVisibleSeq <= 5 <= headSeq 而 5 不在 rows 里。服务端不猜原因，只把三段量交出去。
        assertTrue(page.getHeadSeq() > page.getNextSinceSeq() && !page.isHasMore(),
                "追不平又没更多可拉，是判定空洞的唯一形状");

        // 对照：追平之后 headSeq 与 nextSinceSeq 重新相等，这个信号必须会消失
        messageService.send(conv, A, "空洞之后的那条");
        ImHistoryPage after = messageService.historyPage(conv, A, page.getNextSinceSeq(), 100);
        assertEquals(1, after.getRows().size());
        assertEquals(6L, after.getRows().get(0).getSeq().longValue());
        assertEquals(6L, after.getHeadSeq(), "追平：水位就等于最后一条");
        assertEquals(6L, after.getNextSinceSeq());
    }

    @Test
    public void minVisibleSeqReportsTheCursorThatActuallyBitNotTheOneRequested() {
        Long conv = conversationService.single(A, B, null).getId();
        for (int i = 1; i <= 5; i++) {
            messageService.send(conv, B, "旧消息 " + i);
        }
        assertEquals(5L, messageService.clear(conv, A));
        // 请求的 sinceSeq 在 cleared_seq 之前：生效的是 cleared_seq，minVisibleSeq 要说的是它
        ImHistoryPage page = messageService.historyPage(conv, A, 2L, 100);
        assertTrue(page.getRows().isEmpty());
        assertEquals(6L, page.getMinVisibleSeq(), "本人清到 5，可见下界就是 6，不是客户端要的 3");
        assertEquals(5L, page.getNextSinceSeq(), "游标按生效下限给，客户端下次传它就不会反复撞同一道墙");
        // 对照：B 没清空，同一条请求在 B 那边是另一回事
        ImHistoryPage peer = messageService.historyPage(conv, B, 2L, 100);
        assertEquals(3, peer.getRows().size());
        assertEquals(3L, peer.getMinVisibleSeq());
        assertEquals(5L, peer.getNextSinceSeq());
    }

    @Test
    public void clearedConversationIsInvisibleToMeAndIntactForEveryoneElse() {
        Long conv = conversationService.single(A, B, null).getId();
        for (int i = 1; i <= 3; i++) {
            messageService.send(conv, A, "旧消息 " + i);
        }
        assertEquals(3, messageService.history(conv, A, 0L, 10).size(), "前置：A 现在看得见 3 条");

        long clearedTo = messageService.clear(conv, A);
        assertEquals(3L, clearedTo);
        assertTrue(messageService.history(conv, A, 0L, 10).isEmpty(),
                "清空之后 A 不能再看见任何一条（清空只推游标，所以拉取必须自己带上限）");
        assertNull(messageService.bySeq(conv, A, 1L), "按 seq 反查也不能绕过 cleared_seq");
        assertEquals(0L, readService.unread(conv, A), "清空不该凭空留下未读");

        // 对照一：B 的历史一条不少——清空是我自己的可见游标，不是删库
        assertEquals(3, messageService.history(conv, B, 0L, 10).size(), "别人的历史不能被我的清空抹掉");
        assertNotNull(messageService.bySeq(conv, B, 1L));
        // 对照二：库里那 3 行还在（clear 不删行，加回来的成员能看到完整上下文）
        assertEquals(3, messageMapper.selectCount(new LambdaQueryWrapper<ImMessageDO>()
                .eq(ImMessageDO::getConversationId, conv)).intValue());

        // 清空之后新来的消息照常看得见，游标也不会把未来藏起来
        ImMessageDO fresh = messageService.send(conv, B, "清空之后的新消息");
        assertEquals(4L, fresh.getSeq().longValue());
        List<ImMessageDO> after = messageService.history(conv, A, 0L, 10);
        assertEquals(1, after.size());
        assertEquals(4L, after.get(0).getSeq().longValue());
        assertEquals(1L, readService.unread(conv, A));

        // 清空是幂等的：重放旧的清空请求不能把后来的消息又藏掉
        assertEquals(4L, messageService.clear(conv, A));
        assertTrue(messageService.history(conv, A, 0L, 10).isEmpty());
        assertEquals(5L, messageService.send(conv, A, "再新一点").getSeq().longValue());
    }

    @Test
    public void strangersCannotReadNorWriteWhileMembersBothCan() {
        final Long conv = conversationService.createGroup(A, Collections.singletonList(B),
                "闸门", null, null, ImConvTypes.GROUP).getId();
        assertThrows(ImForbiddenException.class, () -> messageService.send(conv, OUTSIDER, "越权发言"));
        assertThrows(ImForbiddenException.class, () -> messageService.history(conv, OUTSIDER, 0L, 10));
        assertThrows(ImForbiddenException.class, () -> messageService.bySeq(conv, OUTSIDER, 1L));
        assertThrows(ImForbiddenException.class, () -> messageService.headSeq(conv, OUTSIDER));
        assertThrows(ImForbiddenException.class, () -> messageService.clear(conv, OUTSIDER));
        assertThrows(ImForbiddenException.class, () -> messageService.send(987654321L, A, "不存在的会话"));

        // 对照：同一个会话，成员读写都通
        ImMessageDO ok = messageService.send(conv, B, "成员发言");
        assertEquals(1L, ok.getSeq().longValue());
        assertEquals(1, messageService.history(conv, B, 0L, 10).size());
        assertEquals(1L, messageService.headSeq(conv, A));
    }

    // ---------------------------------------------------------------- 内容与类型

    @Test
    public void contentTypeRulesAreEnforcedOnTheWayIn() {
        Long conv = conversationService.single(A, B, null).getId();

        assertThrows(IllegalArgumentException.class, () -> messageService.send(conv, A, "   "),
                "空正文不该占一个 seq");
        assertThrows(IllegalArgumentException.class,
                () -> messageService.send(conv, A, "STICKER", "表情", null, null, null),
                "未登记的 msgType 要挡回来而不是落成一堆方言");
        assertThrows(IllegalArgumentException.class,
                () -> messageService.send(conv, A, "SYS", "系统消息", null, null, null),
                "SYS 只能服务端内部产生，接口调用方伪造它就能冒充系统通知");
        char[] big = new char[5000];
        Arrays.fill(big, 'x');
        assertThrows(IllegalArgumentException.class,
                () -> messageService.send(conv, A, new String(big)),
                "超长正文由 z-msg.im.max-content-length 挡下");
        assertEquals(0L, messageService.headSeq(conv, A), "上面几次失败都不能占号");

        // 对照：登记过的类型都通，非文本类型在列表里显示占位摘要
        ImMessageDO image = messageService.send(conv, A, "IMAGE", "https://cdn/x.png", null, null, null);
        assertEquals("IMAGE", image.getMsgType());
        assertEquals("[图片]", conversationService.requireConversation(conv).getLastMsgPreview());
        messageService.send(conv, A, "FILE", "oss://bucket/report.pdf", null, null, null);
        assertEquals("[文件]", conversationService.requireConversation(conv).getLastMsgPreview());
        messageService.send(conv, A, "AUDIO", "oss://bucket/voice.m4a", null, null, null);
        assertEquals("[语音]", conversationService.requireConversation(conv).getLastMsgPreview());
        assertEquals(3L, messageService.headSeq(conv, A));
    }

    @Test
    public void atUsersAreStoredSortedAndReplyTargetIsKept() {
        Long conv = conversationService.createGroup(A, Arrays.asList(B, OUTSIDER, OUTSIDER_2),
                "@ 与引用", null, null, ImConvTypes.GROUP).getId();
        ImMessageDO first = messageService.send(conv, A, "先看这条");
        // 传进去的顺序是乱的、OUTSIDER_2 还重复了一次：期望值就是"去重 + 升序"那三个 id。
        // （原来这里写成 A,B,OUTSIDER 是把入参里的 OUTSIDER_2 错看成了 OUTSIDER。）
        ImMessageDO m = messageService.send(conv, A, "TEXT", "点名", null,
                Arrays.asList(OUTSIDER_2, A, B, OUTSIDER_2), first.getSeq());
        assertEquals(A + "," + B + "," + OUTSIDER_2, m.getAtUserIds(),
                "at_user_ids 存升序逗号串，客户端不必自己排序去重");
        assertEquals(first.getSeq(), m.getReplyToSeq());
        assertNull(messageService.send(conv, A, "没 @ 任何人").getAtUserIds());
    }

    // ---------------------------------------------------------------- 实时失败不影响落库

    @Test
    public void persistingDoesNotDependOnAnyoneBeingConnected() {
        Long conv = conversationService.single(A, B, null).getId();
        // 这个用例里一条 socket 都没开：publish 命中 0 个连接。落库必须照样成功，
        // 否则"没网就没发出去"会让客户端和用户对账时对不上。
        ImMessageDO sent = messageService.send(conv, A, "没有任何人在线时发的消息");
        assertNotNull(sent.getId());
        assertEquals(1L, sent.getSeq().longValue());
        ImMessageDO row = messageMapper.selectById(sent.getId());
        assertNotNull(row, "返回了 id 但库里没有这一行 = 撒谎");
        assertEquals("没有任何人在线时发的消息", row.getContent());
        assertEquals(1, messageService.history(conv, B, 0L, 10).size(), "对端离线也要能拉到");
        assertEquals(1L, readService.unread(conv, B));
    }

    // ---------------------------------------------------------------- 工具

    private int countByClientMsgId(Long conv, String clientMsgId) {
        List<ImMessageDO> rows = messageMapper.selectList(new LambdaQueryWrapper<ImMessageDO>()
                .eq(ImMessageDO::getConversationId, conv).eq(ImMessageDO::getClientMsgId, clientMsgId));
        return rows == null ? 0 : rows.size();
    }

    private static void assertAscendingByOne(List<ImMessageDO> rows, long from, long to) {
        assertEquals(to - from + 1, rows.size(), "条数: " + seqDump(rows));
        long expect = from;
        for (ImMessageDO r : rows) {
            assertEquals(expect, r.getSeq().longValue(), "必须按 seq 升序连续: " + seqDump(rows));
            expect++;
        }
    }

    private static String seqDump(List<ImMessageDO> rows) {
        StringBuilder sb = new StringBuilder();
        for (ImMessageDO r : rows) {
            sb.append(r.getSeq()).append(' ');
        }
        return sb.toString().trim();
    }
}
