package com.zifang.z.msg.im.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zifang.z.msg.im.config.ImProperties;
import com.zifang.z.msg.im.domain.entity.ImConversationDO;
import com.zifang.z.msg.im.domain.entity.ImMemberDO;
import com.zifang.z.msg.im.domain.mapper.ImConversationMapper;
import com.zifang.z.msg.im.domain.mapper.ImMemberMapper;
import com.zifang.z.msg.im.domain.model.ImConvTypes;
import com.zifang.z.msg.im.domain.model.ImConversationView;
import com.zifang.z.msg.im.domain.model.ImForbiddenException;
import com.zifang.z.msg.im.domain.model.ImNotFoundException;
import com.zifang.z.msg.im.domain.model.ImRoles;
import com.zifang.z.msg.im.domain.model.ImUnread;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话与成员（单聊 create-or-get、群聊建团、加人/踢人/改角色、我的会话列表）。
 * <p>
 * 三条贯穿全类的规则：
 * <ol>
 *   <li><b>"谁"永远是调用方传进来的登录身份，不是请求里自报的 userId</b>：
 *       每个读方法都自带 {@link #requireMember} 这道闸，跨会话的越权不是"忘了判"而是"写不出"；</li>
 *   <li><b>并发撞唯一索引时重读，不把异常甩给调用方</b>：单聊的 {@code uk_pair}
 *       与成员的 {@code uk_im_member_conv_user} 都会在对端同时发起时撞车，
 *       这时正确动作是把对方刚建好的那一行读回来；</li>
 *   <li><b>计数只重算不增量</b>：{@code member_count} 由
 *       {@link ImConversationMapper#refreshMemberCount} 按成员表实数覆盖，
 *       一次失败的加人/重试过的踢人不会让它和真实行数漂移。</li>
 * </ol>
 */
@Service
public class ImConversationService {

    private static final Logger log = LogManager.getLogger(ImConversationService.class);

    @Resource
    private ImConversationMapper conversationMapper;
    @Resource
    private ImMemberMapper memberMapper;
    @Resource
    private ImProperties properties;

    // ---------------------------------------------------------------- 建会话

    /**
     * 单聊 create-or-get：同一对用户永远只有这一个会话，靠 {@code uk_pair} 保证。
     *
     * @param me   发起方（登录身份）
     * @param peer 对端
     */
    public ImConversationDO single(Long me, Long peer, String tenantCode) {
        if (me == null || peer == null) {
            throw new IllegalArgumentException("单聊需要两个 userId");
        }
        if (me.longValue() == peer.longValue()) {
            throw new IllegalArgumentException("不能和自己建单聊（本模块不提供\"文件传输助手\"这类自会话）");
        }
        String pair = ImRoles.singlePairKey(me, peer);
        ImConversationDO existing = findByPairKey(pair);
        if (existing != null) {
            // 会话可能是一年前建的，成员行却是被踢掉的：这里补齐，不做"有会话就当没事"
            insertMemberIfAbsent(existing.getId(), me, ImRoles.MEMBER);
            insertMemberIfAbsent(existing.getId(), peer, ImRoles.MEMBER);
            refreshMemberCount(existing.getId());
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        ImConversationDO conv = new ImConversationDO();
        conv.setConvType(ImConvTypes.SINGLE);
        conv.setTenantCode(tenantCode);
        conv.setOwnerUserId(me);
        conv.setLastMsgSeq(0L);
        conv.setMemberCount(0);
        conv.setUkPair(pair);
        conv.setCreatedTime(now);
        conv.setUpdatedTime(now);
        try {
            conversationMapper.insert(conv);
        } catch (DuplicateKeyException raced) {
            // 对端同一瞬间也点了进来：他建的那一条就是我们要的，重读而不是报错
            ImConversationDO winner = findByPairKey(pair);
            if (winner == null) {
                throw new IllegalStateException("uk_pair 撞了却读不回会话行: " + pair, raced);
            }
            log.info("[z-msg-im] 单聊并发建会话命中 uk_pair={}，复用对方建的 {}", pair, winner.getId());
            insertMemberIfAbsent(winner.getId(), me, ImRoles.MEMBER);
            insertMemberIfAbsent(winner.getId(), peer, ImRoles.MEMBER);
            refreshMemberCount(winner.getId());
            return winner;
        }
        insertMemberIfAbsent(conv.getId(), me, ImRoles.MEMBER);
        insertMemberIfAbsent(conv.getId(), peer, ImRoles.MEMBER);
        refreshMemberCount(conv.getId());
        return reload(conv.getId());
    }

    /**
     * 建群聊 / 聊天室。owner 一定进成员表并且角色是 OWNER。
     *
     * @param convType {@link ImConvTypes#GROUP} 或 {@link ImConvTypes#ROOM}
     */
    public ImConversationDO createGroup(Long owner, List<Long> memberUserIds, String title,
                                        String avatar, String tenantCode, String convType) {
        String type = ImConvTypes.normalize(convType);
        if (ImConvTypes.isSingle(type)) {
            throw new IllegalArgumentException("SINGLE 请走 single()，它要两个 userId");
        }
        if (owner == null) {
            throw new IllegalArgumentException("建群必须有 owner");
        }
        List<Long> members = ImRoles.distinct(memberUserIds);
        if (!members.contains(owner)) {
            members.add(0, owner);
        }
        if (members.size() > properties.getMaxMembersPerConversation()) {
            throw new IllegalArgumentException("一次建群最多 " + properties.getMaxMembersPerConversation()
                    + " 人（z-msg.im.max-members-per-conversation），当前 " + members.size() + " 人");
        }
        LocalDateTime now = LocalDateTime.now();
        ImConversationDO conv = new ImConversationDO();
        conv.setConvType(type);
        conv.setTenantCode(tenantCode);
        conv.setTitle(title == null || title.trim().isEmpty() ? "会话" : title.trim());
        conv.setAvatar(avatar);
        conv.setOwnerUserId(owner);
        conv.setLastMsgSeq(0L);
        conv.setMemberCount(0);
        conv.setUkPair(null);
        conv.setCreatedTime(now);
        conv.setUpdatedTime(now);
        conversationMapper.insert(conv);
        for (Long userId : members) {
            insertMemberIfAbsent(conv.getId(), userId, owner.equals(userId) ? ImRoles.OWNER : ImRoles.MEMBER);
        }
        refreshMemberCount(conv.getId());
        return reload(conv.getId());
    }

    // ---------------------------------------------------------------- 读

    /**
     * 我的会话列表（分页）。返回行里带 {@code lastMsgSeq} / {@code lastMsgPreview} /
     * {@code memberCount} 与本人游标，未读数在库里已经算好，客户端不必再拉一遍消息表。
     */
    public IPage<ImConversationView> pageMine(Long me, int page, int size) {
        if (me == null) {
            throw new IllegalArgumentException("缺少调用者身份");
        }
        Page<ImConversationView> p = new Page<ImConversationView>(Math.max(1, page), clampSize(size));
        IPage<ImConversationView> out = conversationMapper.selectMinePage(p, me);
        List<ImConversationView> records = out.getRecords();
        if (records != null) {
            for (ImConversationView v : records) {
                v.setUnreadCount(ImUnread.of(v.getLastMsgSeq(), v.getMyLastReadSeq(), v.getMyClearedSeq()));
            }
        }
        return out;
    }

    /**
     * 我的全部会话（不分页）：未读汇总用。上限就是 {@code z-msg.im.max-page-size}，
     * 一次拉完比让客户端翻页算总数诚实。
     */
    public List<ImConversationView> listMine(Long me) {
        if (me == null) {
            throw new IllegalArgumentException("缺少调用者身份");
        }
        List<ImConversationView> rows = conversationMapper.selectMineList(me);
        List<ImConversationView> out = rows == null ? new ArrayList<ImConversationView>() : rows;
        int cap = Math.max(1, properties.getMaxPageSize());
        if (out.size() > cap) {
            log.warn("[z-msg-im] userId={} 的会话数 {} 超过 z-msg.im.max-page-size={}，汇总只取前 {}",
                    me, out.size(), cap, cap);
            out = new ArrayList<ImConversationView>(out.subList(0, cap));
        }
        for (ImConversationView v : out) {
            v.setUnreadCount(ImUnread.of(v.getLastMsgSeq(), v.getMyLastReadSeq(), v.getMyClearedSeq()));
        }
        return out;
    }

    /**
     * 成员列表。只有成员能看，且看不到别人的会话。
     */
    public List<ImMemberDO> members(Long me, Long conversationId) {
        requireMember(conversationId, me);
        LambdaQueryWrapper<ImMemberDO> qw = new LambdaQueryWrapper<ImMemberDO>();
        qw.eq(ImMemberDO::getConversationId, conversationId).orderByAsc(ImMemberDO::getId);
        List<ImMemberDO> rows = memberMapper.selectList(qw);
        return rows == null ? Collections.<ImMemberDO>emptyList() : rows;
    }

    public ImConversationDO requireConversation(Long conversationId) {
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        ImConversationDO conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new ImNotFoundException("会话 " + conversationId + " 不存在");
        }
        return conv;
    }

    /**
     * 成员判定。<b>不存在</b>与<b>不是成员</b>回同一句话，不给拿错误码枚举会话 id 的 oracle。
     */
    public ImMemberDO requireMember(Long conversationId, Long userId) {
        if (userId == null) {
            throw new ImForbiddenException("缺少调用者身份");
        }
        ImMemberDO member = findMember(conversationId, userId);
        if (member == null) {
            throw ImForbiddenException.notMember(conversationId, userId);
        }
        return member;
    }

    public boolean isMember(Long conversationId, Long userId) {
        return conversationId != null && userId != null && findMember(conversationId, userId) != null;
    }

    /**
     * 会话是否存在（授权策略用它区分"这条 room 不是我管的"与"是我的但你不是成员"）。
     */
    public boolean conversationExists(Long conversationId) {
        if (conversationId == null) {
            return false;
        }
        return conversationMapper.selectCount(new LambdaQueryWrapper<ImConversationDO>()
                .eq(ImConversationDO::getId, conversationId)) > 0;
    }

    // ---------------------------------------------------------------- 成员管理

    /**
     * 加人。只有 OWNER/ADMIN 可以；重复加同一个人在 {@code uk_im_member_conv_user} 上
     * 命中即视为成功（幂等），不报错。
     */
    public List<ImMemberDO> addMembers(Long operator, Long conversationId, List<Long> userIds) {
        requireManager(conversationId, operator);
        List<Long> ids = ImRoles.distinct(userIds);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("userIds 不能为空");
        }
        ImConversationDO conv = requireConversation(conversationId);
        int current = conv.getMemberCount() == null ? 0 : conv.getMemberCount().intValue();
        for (Long userId : ids) {
            if (findMember(conversationId, userId) == null) {
                current++;
            }
            if (current > properties.getMaxMembersPerConversation()) {
                throw new IllegalArgumentException("会话 " + conversationId + " 成员已达上限 "
                        + properties.getMaxMembersPerConversation() + "（z-msg.im.max-members-per-conversation）");
            }
            insertMemberIfAbsent(conversationId, userId, ImRoles.MEMBER);
        }
        refreshMemberCount(conversationId);
        return members(operator, conversationId);
    }

    /**
     * 踢人。OWNER 不能被踢；管理员也不能把自己踢成最后一个 owner。
     * 退群只删成员行，消息与回执都留着（回执是"他读过"这段事实，不随人消失）。
     */
    public int removeMembers(Long operator, Long conversationId, List<Long> userIds) {
        requireManager(conversationId, operator);
        List<Long> ids = ImRoles.distinct(userIds);
        if (ids.contains(operator)) {
            throw new IllegalArgumentException("不能用踢人的方式退出会话，请走 leave()");
        }
        int removed = 0;
        for (Long userId : ids) {
            ImMemberDO target = findMember(conversationId, userId);
            if (target == null) {
                continue;
            }
            if (ImRoles.OWNER.equals(target.getRole())) {
                throw new IllegalArgumentException("OWNER 不能被踢出会话");
            }
            LambdaQueryWrapper<ImMemberDO> qw = new LambdaQueryWrapper<ImMemberDO>();
            qw.eq(ImMemberDO::getConversationId, conversationId).eq(ImMemberDO::getUserId, userId);
            removed += memberMapper.delete(qw);
        }
        if (removed > 0) {
            refreshMemberCount(conversationId);
        }
        return removed;
    }

    /**
     * 自己退群。不需要管理员权限，但 owner 要先转让（避免留下没有 owner 的群）。
     */
    public boolean leave(Long userId, Long conversationId) {
        ImMemberDO me = requireMember(conversationId, userId);
        if (ImRoles.OWNER.equals(me.getRole())) {
            throw new IllegalArgumentException("owner 退出前请先用 setRole 转让群主");
        }
        LambdaQueryWrapper<ImMemberDO> qw = new LambdaQueryWrapper<ImMemberDO>();
        qw.eq(ImMemberDO::getConversationId, conversationId).eq(ImMemberDO::getUserId, userId);
        boolean gone = memberMapper.delete(qw) > 0;
        if (gone) {
            refreshMemberCount(conversationId);
        }
        return gone;
    }

    /**
     * 改角色。只有 OWNER 能改（含转让群主）；目标必须是本会话成员。
     */
    public ImMemberDO setRole(Long operator, Long conversationId, Long targetUserId, String role) {
        ImMemberDO by = requireMember(conversationId, operator);
        if (!ImRoles.OWNER.equals(by.getRole())) {
            throw new ImForbiddenException("只有群主可以改角色");
        }
        ImMemberDO target = requireMember(conversationId, targetUserId);
        String next = ImRoles.normalize(role);
        if (ImRoles.OWNER.equals(target.getRole()) && !ImRoles.OWNER.equals(next)) {
            throw new IllegalArgumentException("不能直接摘掉 OWNER，请对新人 setRole(OWNER) 完成转让");
        }
        LambdaQueryWrapper<ImMemberDO> qw = new LambdaQueryWrapper<ImMemberDO>();
        qw.eq(ImMemberDO::getConversationId, conversationId).eq(ImMemberDO::getUserId, targetUserId);
        List<ImMemberDO> owners = memberMapper.selectList(new LambdaQueryWrapper<ImMemberDO>()
                .eq(ImMemberDO::getConversationId, conversationId).eq(ImMemberDO::getRole, ImRoles.OWNER));
        if (ImRoles.OWNER.equals(next) && (owners == null || owners.isEmpty())) {
            throw new IllegalStateException("会话 " + conversationId + " 没有 OWNER 行，转让无法进行");
        }
        if (ImRoles.OWNER.equals(next) && owners != null) {
            // 转让：先把旧 owner 降成 ADMIN，保证 OWNER 永远只有一个（不是一句 SQL 能保证的，
            // 所以在这里按顺序做两次条件更新）
            for (ImMemberDO owner : owners) {
                if (owner.getUserId().equals(targetUserId)) {
                    continue;
                }
                LambdaUpdateWrapper<ImMemberDO> demote = new LambdaUpdateWrapper<ImMemberDO>();
                demote.eq(ImMemberDO::getId, owner.getId()).set(ImMemberDO::getRole, ImRoles.ADMIN);
                memberMapper.update(null, demote);
            }
        }
        LambdaUpdateWrapper<ImMemberDO> set = new LambdaUpdateWrapper<ImMemberDO>();
        set.eq(ImMemberDO::getId, target.getId()).set(ImMemberDO::getRole, next);
        memberMapper.update(null, set);
        return findMember(conversationId, targetUserId);
    }

    /**
     * 免打扰开关。仍然计未读，只是不弹 —— 与 {@code z_msg_im_member.muted} 的注释一致。
     */
    public boolean mute(Long userId, Long conversationId, boolean muted) {
        requireMember(conversationId, userId);
        LambdaUpdateWrapper<ImMemberDO> qw = new LambdaUpdateWrapper<ImMemberDO>();
        qw.eq(ImMemberDO::getConversationId, conversationId).eq(ImMemberDO::getUserId, userId)
                .set(ImMemberDO::getMuted, muted ? 1 : 0)
                .set(ImMemberDO::getUpdatedTime, LocalDateTime.now());
        return memberMapper.update(null, qw) > 0;
    }

    // ---------------------------------------------------------------- 内部工具

    public ImMemberDO findMember(Long conversationId, Long userId) {
        if (conversationId == null || userId == null) {
            return null;
        }
        LambdaQueryWrapper<ImMemberDO> qw = new LambdaQueryWrapper<ImMemberDO>();
        qw.eq(ImMemberDO::getConversationId, conversationId).eq(ImMemberDO::getUserId, userId);
        return memberMapper.selectOne(qw);
    }

    private ImMemberDO requireManager(Long conversationId, Long operator) {
        ImMemberDO me = requireMember(conversationId, operator);
        if (!ImRoles.isManager(me.getRole())) {
            throw new ImForbiddenException("只有 OWNER/ADMIN 可以管理成员");
        }
        return me;
    }

    private ImConversationDO findByPairKey(String pair) {
        LambdaQueryWrapper<ImConversationDO> qw = new LambdaQueryWrapper<ImConversationDO>();
        qw.eq(ImConversationDO::getUkPair, pair);
        return conversationMapper.selectOne(qw);
    }

    private ImConversationDO reload(Long conversationId) {
        ImConversationDO fresh = conversationMapper.selectById(conversationId);
        return fresh == null ? requireConversation(conversationId) : fresh;
    }

    /**
     * 幂等加人：已有行直接返回，并发重复插入撞唯一索引也当作成功（重读那一行）。
     */
    private void insertMemberIfAbsent(Long conversationId, Long userId, String role) {
        if (findMember(conversationId, userId) != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ImMemberDO member = new ImMemberDO();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(role);
        member.setLastReadSeq(0L);
        member.setMuted(0);
        member.setPushSwitch(1);
        member.setClearedSeq(0L);
        member.setJoinedTime(now);
        member.setUpdatedTime(now);
        try {
            memberMapper.insert(member);
        } catch (DuplicateKeyException raced) {
            if (log.isTraceEnabled()) {
                log.trace("[z-msg-im] 成员并发重复插入 conversation={} user={}", conversationId, userId);
            }
        }
    }

    private void refreshMemberCount(Long conversationId) {
        conversationMapper.refreshMemberCount(conversationId);
    }

    private int clampSize(int size) {
        int s = size <= 0 ? properties.getDefaultPageSize() : size;
        return Math.min(s, Math.max(1, properties.getMaxPageSize()));
    }
}
