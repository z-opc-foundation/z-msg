package com.zifang.z.msg.im.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zifang.z.msg.im.domain.entity.ImConversationDO;
import com.zifang.z.msg.im.domain.entity.ImMemberDO;
import com.zifang.z.msg.im.domain.entity.ImReadReceiptDO;
import com.zifang.z.msg.im.domain.mapper.ImMemberMapper;
import com.zifang.z.msg.im.domain.mapper.ImReadReceiptMapper;
import com.zifang.z.msg.im.domain.model.ImConversationView;
import com.zifang.z.msg.im.domain.model.ImUnread;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 已读与未读。
 * <p>
 * 两份游标，各自的职责分得很开：
 * <ul>
 *   <li>{@code z_msg_im_member.last_read_seq} —— 我自己的水位，未读数由它算，
 *       写在成员行上（一行一列，写放大最小）；</li>
 *   <li>{@code z_msg_im_read_receipt.last_read_seq} —— 给<em>别人</em>看的展示态：
 *       群里"谁读到哪"。这就是回执表存在的唯一理由，不写成员表是因为成员行会随退群消失，
 *       而"他读过"是已经发生的事实。</li>
 * </ul>
 * 单调性由 SQL 谓词保证（{@code AND last_read_seq < #{seq}}），不是读出来在 Java 里比一比
 * 再写回去 —— 后者在两次并发标已读之间会插入一次读-改-写竞态，把新值盖回旧值。
 * <p>
 * 闸门顺序与 {@link ImMessageService} 一致：<b>先判成员、再读会话行</b>，所以
 * "不属于我的会话"与"不存在的会话"在这里回同一句 403，404 只留给成员行还在而会话行没了
 * 这类真的读不到的场景。
 */
@Service
public class ImReadService {

    private static final Logger log = LogManager.getLogger(ImReadService.class);

    @Resource
    private ImMemberMapper memberMapper;
    @Resource
    private ImReadReceiptMapper receiptMapper;
    @Resource
    private ImConversationService conversationService;
    @Resource
    private ImMessageService messageService;

    /**
     * 标记已读：把本人在该会话里的游标推到 {@code uptoSeq}。
     * <p>
     * 三条不变式：
     * <ol>
     *   <li>目标值以会话水位 {@code last_msg_seq} 封顶 —— 客户端谎报"我读到 999"
     *       不能把还没发的消息算成已读；</li>
     *   <li>只前进不回退 —— 迟到的旧回执（弱网重传、多端时钟差）改不动新值，
     *       返回值就是"当前真实游标"，调用方据此纠正本地 UI；</li>
     *   <li>回执表 upsert 幂等 —— 同一 (会话, 人) 永远一行，靠
     *       {@code uk_im_receipt_conv_user}。</li>
     * </ol>
     *
     * @return 标记后的游标（若传进来的是旧值，返回的是没被改动的原值）
     */
    public long markRead(Long conversationId, Long userId, long uptoSeq) {
        conversationService.requireMember(conversationId, userId);
        ImConversationDO conv = conversationService.requireConversation(conversationId);
        long head = conv.getLastMsgSeq() == null ? 0L : conv.getLastMsgSeq().longValue();
        long target = Math.min(uptoSeq < 0 ? 0L : uptoSeq, head);

        int moved = memberMapper.advanceReadSeq(conversationId, userId, target);
        upsertReceipt(conversationId, userId, target);
        if (moved == 0 && log.isDebugEnabled()) {
            log.debug("[z-msg-im] 已读游标未前进 conversation={} user={} 请求={}（旧值或已领先）",
                    conversationId, userId, target);
        }
        long effective = currentReadSeq(conversationId, userId);
        messageService.publishReadFrame(conversationId, userId, effective, head);
        return effective;
    }

    /**
     * 未读数：{@code 会话水位 - max(已读游标, 清空游标)}，下限 0。
     */
    public long unread(Long conversationId, Long userId) {
        ImMemberDO member = conversationService.requireMember(conversationId, userId);
        ImConversationDO conv = conversationService.requireConversation(conversationId);
        return ImUnread.of(conv.getLastMsgSeq(), member.getLastReadSeq(), member.getClearedSeq());
    }

    /**
     * 未读汇总：本人每个会话一条 + 总数。
     *
     * @return {@code total}（所有会话未读之和）与 {@code items}（逐会话明细）
     */
    public Map<String, Object> unreadSummary(Long userId) {
        List<ImConversationView> rows = conversationService.listMine(userId);
        List<ImUnread> items = new ArrayList<ImUnread>(rows.size());
        long total = 0L;
        for (ImConversationView v : rows) {
            ImUnread u = ImUnread.of(v.getConversationId(), v.getConvType(), v.getTitle(),
                    v.getLastMsgSeq(), v.getMyLastReadSeq(), v.getMyClearedSeq(), v.getMuted());
            total += u.getUnreadCount();
            items.add(u);
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("total", total);
        out.put("conversationCount", items.size());
        out.put("items", items);
        return out;
    }

    /**
     * "谁读到哪"。可见性：只有同会话成员读得到 —— 群聊已读状态本身就是社交信息，
     * 不能让外人拿会话 id 来枚举。返回不含本人以外的人的隐私字段（就三列，够用）。
     */
    public List<ImReadReceiptDO> receipts(Long conversationId, Long caller) {
        conversationService.requireMember(conversationId, caller);
        LambdaQueryWrapper<ImReadReceiptDO> qw = new LambdaQueryWrapper<ImReadReceiptDO>();
        qw.eq(ImReadReceiptDO::getConversationId, conversationId)
                .orderByAsc(ImReadReceiptDO::getUserId);
        List<ImReadReceiptDO> rows = receiptMapper.selectList(qw);
        return rows == null ? Collections.<ImReadReceiptDO>emptyList() : rows;
    }

    public long currentReadSeq(Long conversationId, Long userId) {
        ImMemberDO member = conversationService.requireMember(conversationId, userId);
        return member.getLastReadSeq() == null ? 0L : member.getLastReadSeq().longValue();
    }

    /**
     * 条件更新 → 0 行则插入 → 插入撞唯一索引就再更新一次。
     * 三步都在没有事务的情况下收敛到一行，是因为
     * {@code uk_im_receipt_conv_user} 让"并发重复插入"只能有一个赢家，
     * 输的那个第二次条件更新必然命中赢家的行。
     */
    private void upsertReceipt(Long conversationId, Long userId, long seq) {
        if (seq <= 0L) {
            return;
        }
        if (receiptMapper.advanceReadSeq(conversationId, userId, seq) > 0) {
            return;
        }
        LambdaQueryWrapper<ImReadReceiptDO> existing = new LambdaQueryWrapper<ImReadReceiptDO>();
        existing.eq(ImReadReceiptDO::getConversationId, conversationId).eq(ImReadReceiptDO::getUserId, userId);
        ImReadReceiptDO row = receiptMapper.selectOne(existing);
        if (row == null) {
            ImReadReceiptDO fresh = new ImReadReceiptDO();
            LocalDateTime now = LocalDateTime.now();
            fresh.setConversationId(conversationId);
            fresh.setUserId(userId);
            fresh.setLastReadSeq(seq);
            fresh.setCreatedTime(now);
            fresh.setUpdatedTime(now);
            try {
                receiptMapper.insert(fresh);
                return;
            } catch (DuplicateKeyException raced) {
                // 另一个端同时在标已读：它插的那一行就是我们要更新的那一行
                if (log.isTraceEnabled()) {
                    log.trace("[z-msg-im] 回执并发插入 conversation={} user={}", conversationId, userId);
                }
            }
        }
        // 走到这里说明行已在、且带来的值不比它高（或者刚被别人插好）：再试一次，
        // 只前进不回退的语义由 SQL 谓词守住，这里不会把别人的更高水位盖下来。
        receiptMapper.advanceReadSeq(conversationId, userId, seq);
    }
}
