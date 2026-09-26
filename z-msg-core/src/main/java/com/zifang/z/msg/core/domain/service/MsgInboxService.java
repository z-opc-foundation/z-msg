package com.zifang.z.msg.core.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zifang.z.msg.core.config.MessageProperties;
import com.zifang.z.msg.core.domain.entity.InAppMessage;
import com.zifang.z.msg.core.domain.mapper.InAppMessageMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 站内信读侧（1.1.0）。
 * <p>
 * 1.0.0 的收件箱查询散在 controller 里，因此带上三个后果，这里逐条堵掉：
 * <ul>
 *   <li>{@code userId} 是可信入参 —— 任何人改一下 query 就能读、删别人的收件箱；
 *       现在每个方法的 WHERE 里都有 {@code user_id}，越权不是"忘了判"而是"写不出"；</li>
 *   <li>列表把 {@code content}（CLOB）整坨带回 —— 列表只取展示列，正文留给详情；</li>
 *   <li>{@code expire_at} 与 {@code deleted} 不过滤 —— 已过期/已删的消息继续占未读红点。</li>
 * </ul>
 * 删除一律软删（{@code deleted=1}）：原 {@code /delete-all} 是物理删，一个误参数就把用户全部历史清空。
 */
@Service
public class MsgInboxService {

    @Resource
    private InAppMessageMapper mapper;
    @Resource
    private MessageProperties properties;

    /**
     * 我的收件箱分页。置顶优先，其次时间倒序。
     *
     * @param userId     当前登录用户，不是"想查谁"
     * @param unreadOnly 可空；true 只看未读
     * @param msgType    可空；按消息类型过滤
     */
    public IPage<InAppMessage> pageMine(Long userId, int pageNum, int size,
                                        Boolean unreadOnly, String msgType) {
        if (userId == null) {
            return new Page<InAppMessage>(Math.max(1, pageNum), 0);
        }
        LambdaQueryWrapper<InAppMessage> qw = new LambdaQueryWrapper<InAppMessage>();
        qw.eq(InAppMessage::getUserId, userId);
        applyAliveFilter(qw);
        if (Boolean.TRUE.equals(unreadOnly)) {
            qw.eq(InAppMessage::getIsRead, 0);
        }
        if (msgType != null && !msgType.isEmpty()) {
            qw.eq(InAppMessage::getMsgType, msgType);
        }
        // userId 在这份投影里是故意留下的：它不是展示字段，但它是宿主唯一能自证
        // "这一行为什么在我这儿"的列。省掉一个 BIGINT 换不来什么，换来的是宿主
        // 拿到一排 userId=null 的行、越权回归时谁都发现不了（正文 content 才是省的那一列）。
        qw.select(InAppMessage::getId, InAppMessage::getUserId, InAppMessage::getMsgId,
                InAppMessage::getEventType,
                InAppMessage::getMsgType, InAppMessage::getTitle, InAppMessage::getLinkUrl,
                InAppMessage::getIsRead, InAppMessage::getPriority, InAppMessage::getPinned,
                InAppMessage::getReadTime, InAppMessage::getExpireAt, InAppMessage::getCreatedTime);
        qw.orderByDesc(InAppMessage::getPinned).orderByDesc(InAppMessage::getCreatedTime);
        return mapper.selectPage(new Page<InAppMessage>(Math.max(1, pageNum), clampSize(size)), qw);
    }

    /**
     * 未读数，口径与列表一致：过期与已删的不计入红点。
     */
    public long unreadCount(Long userId) {
        if (userId == null) {
            return 0L;
        }
        LambdaQueryWrapper<InAppMessage> qw = new LambdaQueryWrapper<InAppMessage>();
        qw.eq(InAppMessage::getUserId, userId).eq(InAppMessage::getIsRead, 0);
        applyAliveFilter(qw);
        Long n = mapper.selectCount(qw);
        return n == null ? 0L : n.longValue();
    }

    /**
     * 标记已读。
     *
     * @return true 只有"这条确实是你的、且之前未读"才成立
     */
    public boolean markRead(Long userId, Long messageId) {
        if (userId == null || messageId == null) {
            return false;
        }
        LambdaUpdateWrapper<InAppMessage> uw = new LambdaUpdateWrapper<InAppMessage>();
        uw.eq(InAppMessage::getId, messageId)
                .eq(InAppMessage::getUserId, userId)
                .eq(InAppMessage::getIsRead, 0)
                .set(InAppMessage::getIsRead, 1)
                .set(InAppMessage::getReadTime, LocalDateTime.now());
        return mapper.update(null, uw) > 0;
    }

    public int markAllRead(Long userId) {
        if (userId == null) {
            return 0;
        }
        LambdaUpdateWrapper<InAppMessage> uw = new LambdaUpdateWrapper<InAppMessage>();
        uw.eq(InAppMessage::getUserId, userId)
                .eq(InAppMessage::getIsRead, 0)
                .set(InAppMessage::getIsRead, 1)
                .set(InAppMessage::getReadTime, LocalDateTime.now());
        return mapper.update(null, uw);
    }

    /**
     * 软删一条。别人的 id 传进来得到 false，而不是把别人的消息删掉。
     */
    public boolean softDelete(Long userId, Long messageId) {
        if (userId == null || messageId == null) {
            return false;
        }
        LambdaUpdateWrapper<InAppMessage> uw = new LambdaUpdateWrapper<InAppMessage>();
        uw.eq(InAppMessage::getId, messageId)
                .eq(InAppMessage::getUserId, userId)
                .eq(InAppMessage::getDeleted, 0)
                .set(InAppMessage::getDeleted, 1);
        return mapper.update(null, uw) > 0;
    }

    /**
     * 清空"我的"收件箱（软删）。返回被标记的条数。
     */
    public int softDeleteAll(Long userId, boolean onlyRead) {
        if (userId == null) {
            return 0;
        }
        LambdaUpdateWrapper<InAppMessage> uw = new LambdaUpdateWrapper<InAppMessage>();
        uw.eq(InAppMessage::getUserId, userId)
                .eq(InAppMessage::getDeleted, 0);
        if (onlyRead) {
            uw.eq(InAppMessage::getIsRead, 1);
        }
        uw.set(InAppMessage::getDeleted, 1);
        return mapper.update(null, uw);
    }

    /**
     * @return 属于该用户时返回完整行（含正文）；否则 null —— 越权与不存在不可区分，避免探测
     */
    public InAppMessage detail(Long userId, Long messageId) {
        if (userId == null || messageId == null) {
            return null;
        }
        LambdaQueryWrapper<InAppMessage> qw = new LambdaQueryWrapper<InAppMessage>();
        qw.eq(InAppMessage::getId, messageId).eq(InAppMessage::getUserId, userId);
        InAppMessage row = mapper.selectOne(qw);
        if (row == null || (row.getDeleted() != null && row.getDeleted() == 1)) {
            return null;
        }
        return row;
    }

    /**
     * "还活着"= 未被用户删除，且没有过期。所有读侧查询共用这一个口径。
     */
    private void applyAliveFilter(LambdaQueryWrapper<InAppMessage> qw) {
        qw.eq(InAppMessage::getDeleted, 0);
        final LocalDateTime now = LocalDateTime.now();
        qw.and(w -> w.isNull(InAppMessage::getExpireAt).or().gt(InAppMessage::getExpireAt, now));
    }

    private int clampSize(int size) {
        int def = properties.getInbox().getDefaultPageSize();
        int max = properties.getInbox().getMaxPageSize();
        int n = size <= 0 ? def : size;
        return Math.min(Math.max(1, n), Math.max(1, max));
    }
}
