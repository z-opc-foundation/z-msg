package com.zifang.z.msg.im.domain.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zifang.z.msg.im.config.ImProperties;
import com.zifang.z.msg.im.domain.entity.ImConversationDO;
import com.zifang.z.msg.im.domain.mapper.ImConversationMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 会话内 seq 分配器：单点负责"严格单调递增、不重号"这条契约。
 * <p>
 * 做法是对 {@code z_msg_im_conversation.last_msg_seq} 做 CAS（compare-and-set）：
 * {@code UPDATE ... SET last_msg_seq = expect + 1 WHERE id = ? AND last_msg_seq = expect}。
 * 影响 0 行就说明水位被别的线程抬走了，重读再试。三条理由：
 * <ol>
 *   <li><b>不重号</b>：同一行上的 UPDATE 在数据库里被行锁串行化，两个线程不可能都命中
 *       同一个 expect；唯一索引 {@code uk_im_msg_conv_seq} 是第二道防线，不是第一道。</li>
 *   <li><b>不空洞</b>：每次成功恰好 +1，没有"批量预取一段号"的窗口。</li>
 *   <li><b>不需要事务管理器</b>：一条语句自己就是原子的。本工程
 *       {@code sqlSessionFactoryMsg} 没有绑定 {@code PlatformTransactionManager}，
 *       自动提交下的 {@code SELECT ... FOR UPDATE} 锁随语句结束就没了，用它做占号是假的。</li>
 * </ol>
 * 水位与 {@code last_msg_id} / {@code last_msg_preview} 写在同一条 UPDATE 里，
 * 所以会话列表上的"最后一条消息"永远对应库里最大的 seq。
 * <p>
 * 已知代价：CAS 成功但随后的 INSERT 失败时（唯一索引 {@code uk_im_msg_conv_client}
 * 撞在并发重复提交上），这个 seq 就空在那里 —— 会话水位比最大消息 seq 大 1。
 * 客户端按 {@code seq > sinceSeq} 的区间语义拉取，空洞对它不可见。
 */
@Service
public class ImSequencer {

    private static final Logger log = LogManager.getLogger(ImSequencer.class);

    @Resource
    private ImConversationMapper conversationMapper;
    @Resource
    private ImProperties properties;

    /**
     * 占一个号，并把"会话的最后一条消息是谁"一并落下去。
     *
     * @param conversation 调用方已经读到的会话行（其 {@code lastMsgSeq} 只是 CAS 的初始 guess）
     * @param msgId        即将插入的消息 id（调用方先用 {@link #newMessageId()} 拿到）
     * @param preview      会话列表摘要
     * @return 分配到的 seq，会话内从 1 开始严格递增
     */
    public long append(ImConversationDO conversation, long msgId, String preview) {
        if (conversation == null || conversation.getId() == null) {
            throw new IllegalArgumentException("conversation 不能为空");
        }
        Long id = conversation.getId();
        long expect = conversation.getLastMsgSeq() == null ? 0L : conversation.getLastMsgSeq().longValue();
        int maxAttempts = Math.max(1, properties.getSeqCasMaxAttempts());
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (conversationMapper.casAppend(id, expect, msgId, preview) == 1) {
                return expect + 1L;
            }
            ImConversationDO current = conversationMapper.selectById(id);
            if (current == null) {
                throw new IllegalStateException("会话不存在，无法分配 seq: " + id);
            }
            long fresh = current.getLastMsgSeq() == null ? 0L : current.getLastMsgSeq().longValue();
            if (log.isTraceEnabled()) {
                log.trace("[z-msg-im] seq CAS 失败 conversation={} expect={} 重读到={} attempt={}",
                        id, expect, fresh, attempt);
            }
            expect = fresh;
            sleepQuietly(properties.getSeqCasBackoffMs());
        }
        throw new IllegalStateException("会话 " + id + " 占号重试 " + maxAttempts
                + " 次仍未成功（写入竞争过热，请调大 z-msg.im.seq-cas-max-attempts）");
    }

    /**
     * 先把 id 拿到手，才能在占号的那一条 UPDATE 里把 {@code last_msg_id} 写对。
     * 用 MP 的雪花发生器，和 {@code @TableId(type = IdType.ASSIGN_ID)} 是同一个源。
     */
    public long newMessageId() {
        return IdWorker.getId();
    }

    private static void sleepQuietly(long ms) {
        if (ms <= 0L) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
