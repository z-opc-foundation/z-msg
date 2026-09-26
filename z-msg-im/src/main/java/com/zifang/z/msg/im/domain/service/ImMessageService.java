package com.zifang.z.msg.im.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zifang.z.msg.api.RealtimeMessage;
import com.zifang.z.msg.api.RealtimePublisher;
import com.zifang.z.msg.api.RealtimeTopics;
import com.zifang.z.msg.im.config.ImProperties;
import com.zifang.z.msg.im.domain.entity.ImConversationDO;
import com.zifang.z.msg.im.domain.entity.ImMemberDO;
import com.zifang.z.msg.im.domain.entity.ImMessageDO;
import com.zifang.z.msg.im.domain.mapper.ImMemberMapper;
import com.zifang.z.msg.im.domain.mapper.ImMessageMapper;
import com.zifang.z.msg.im.domain.model.ImConvTypes;
import com.zifang.z.msg.im.domain.model.ImRoles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发消息与增量拉历史。
 * <p>
 * 三件事按顺序做，顺序本身就是设计：
 * <ol>
 *   <li><b>先查幂等</b>：{@code client_msg_id} 命中就原样返回那一行，既不占号也不新增行
 *       （弱网重发的正确形状是"同一个气泡"，不是"两个气泡"）；</li>
 *   <li><b>再占号</b>：seq 由 {@link ImSequencer} 用一次 CAS 拿到，同时把会话的
 *       {@code last_msg_id} / {@code last_msg_preview} 抬上来；</li>
 *   <li><b>最后落库 + 发帧</b>：实时帧在落库之后发，且整段包在 try/catch 里 ——
 *       推送是增强，一条帧发不出去绝不能让已经写进库的消息变成失败。</li>
 * </ol>
 * <p>
 * 闸门顺序：本类每个入口都是<b>先判成员、再读会话行</b>。"从来不属于我的会话"和
 * "压根不存在的会话"必须回同一句 403（{@code ImForbiddenException.notMember} 的注释就是这条），
 * 先 {@code requireConversation} 会让 404 变成一个能枚举会话 id 的 oracle。
 * 404 只留给成员闸门放行之后确实读不到东西的场景（会话被删而成员行还在、
 * 消息落在本人的 cleared_seq 之前）。
 * <p>
 * 投递面：消息永远先投 {@code room:<conversationId>}（成员才订得到，见
 * {@link com.zifang.z.msg.im.auth.ImTopicAuthorizationPolicy}）；此外按 {@code z-msg.im.user-side-push}
 * 把同一帧投到其它成员的 {@code user:<id>}，让"只订自己收件箱"的轻客户端也能收到 DM。
 * <b>离线的人这里不管</b>：离线可见性由站内信（{@code z_msg_message}）那一层负责，
 * 本模块只管实时帧，不做补偿投递。
 */
@Service
public class ImMessageService {

    private static final Logger log = LogManager.getLogger(ImMessageService.class);

    /** 会话列表摘要的截断长度；{@code last_msg_preview} 是 VARCHAR(512)，留足余量。 */
    private static final int PREVIEW_MAX_CHARS = 120;

    private static final List<String> MSG_TYPES = Collections.unmodifiableList(
            java.util.Arrays.asList("TEXT", "IMAGE", "FILE", "AUDIO", "SYS"));

    @Resource
    private ImMessageMapper messageMapper;
    @Resource
    private ImMemberMapper memberMapper;
    @Resource
    private ImConversationService conversationService;
    @Resource
    private ImSequencer sequencer;
    @Resource
    private ImProperties properties;
    /**
     * 宿主没引 z-msg-ws 时 core 仍然给出一个零 transport 的 publisher；
     * 用 ObjectProvider 是为了"连 core 的实时 bean 都没有"时本模块照样能用（只落库）。
     */
    @Resource
    private ObjectProvider<RealtimePublisher> publisherProvider;

    // ---------------------------------------------------------------- 发消息

    public ImMessageDO send(Long conversationId, Long senderUserId, String content) {
        return send(conversationId, senderUserId, "TEXT", content, null, null, null);
    }

    /**
     * 发消息。
     *
     * @param conversationId 会话
     * @param senderUserId   登录身份，<b>不接受请求里自报的发送者</b>（controller 传的是
     *                       {@code MsgPrincipalResolver#require} 的结果）
     * @param msgType        TEXT / IMAGE / FILE / AUDIO / SYS
     * @param content        正文；IMAGE/FILE/AUDIO 时是引用串（URL 或对象 key），
     *                       本模块不做任何媒体处理
     * @param clientMsgId    客户端本地 id，同一会话内重复提交返回同一条消息
     * @param atUserIds      被 @ 的人，落库存升序逗号分隔
     * @param replyToSeq     引用哪条消息的 seq，可空
     * @return 落库后的消息行（幂等重发时返回的就是那已有的一条，seq 不变）
     */
    public ImMessageDO send(Long conversationId, Long senderUserId, String msgType, String content,
                            String clientMsgId, List<Long> atUserIds, Long replyToSeq) {
        conversationService.requireMember(conversationId, senderUserId);
        ImConversationDO conv = conversationService.requireConversation(conversationId);
        String type = normalizeMsgType(msgType);
        String body = validateContent(content, type);
        String dedupKey = clientMsgId == null || clientMsgId.trim().isEmpty()
                ? null : clientMsgId.trim();

        if (dedupKey != null) {
            ImMessageDO existing = findByClientMsgId(conversationId, dedupKey);
            if (existing != null) {
                log.info("[z-msg-im] clientMsgId={} 在会话 {} 内已存在 msg={}，按幂等成功返回",
                        dedupKey, conversationId, existing.getId());
                return existing;
            }
        }
        if (replyToSeq != null && replyToSeq.longValue() < 0L) {
            throw new IllegalArgumentException("replyToSeq 不能为负");
        }

        ImMessageDO message = new ImMessageDO();
        long id = sequencer.newMessageId();
        message.setId(id);
        message.setConversationId(conversationId);
        message.setSenderUserId(senderUserId);
        message.setMsgType(type);
        message.setContent(body);
        message.setClientMsgId(dedupKey);
        message.setAtUserIds(ImRoles.atUserIds(atUserIds));
        message.setReplyToSeq(replyToSeq);
        message.setTenantCode(conv.getTenantCode());
        LocalDateTime now = LocalDateTime.now();
        message.setCreatedTime(now);

        long seq = sequencer.append(conv, id, previewOf(type, body));
        message.setSeq(seq);
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException e) {
            if (dedupKey != null) {
                // 两个重发请求同时越过了"先查幂等"这一步：唯一索引才是那条幂等保证，
                // 这里把对方插进去的那一条读回来。代价是这个 seq 空在那里（见 ImSequencer 注释）。
                ImMessageDO winner = findByClientMsgId(conversationId, dedupKey);
                if (winner != null) {
                    log.info("[z-msg-im] 并发重复提交命中 uk_im_msg_conv_client conversation={} key={}",
                            conversationId, dedupKey);
                    return winner;
                }
            }
            throw new IllegalStateException("消息写入被唯一索引拒绝（seq 或 client_msg_id 冲突）: "
                    + e.getMessage(), e);
        }
        publishFrame(conv, message, now);
        return message;
    }

    // ---------------------------------------------------------------- 拉历史

    /**
     * 增量拉历史：{@code seq > max(sinceSeq, 本人的 cleared_seq)}，升序，最多 size 条。
     * <p>
     * 下限取 {@code cleared_seq} 是"清空聊天记录"的实现方式 —— 只把自己的可见游标推上去，
     * <b>不删任何消息行</b>：别人的历史还在，将来加回来的成员也能看到完整上下文。
     *
     * @param sinceSeq 客户端手里的水位，负数按 0 处理
     */
    public List<ImMessageDO> history(Long conversationId, Long me, long sinceSeq, int size) {
        ImMemberDO member = conversationService.requireMember(conversationId, me);
        long floor = Math.max(sinceSeq < 0 ? 0L : sinceSeq,
                member.getClearedSeq() == null ? 0L : member.getClearedSeq().longValue());
        LambdaQueryWrapper<ImMessageDO> qw = new LambdaQueryWrapper<ImMessageDO>();
        qw.eq(ImMessageDO::getConversationId, conversationId)
                .gt(ImMessageDO::getSeq, floor)
                .orderByAsc(ImMessageDO::getSeq);
        // 条数上限必须落到 SQL 的 LIMIT 上（Page + PaginationInnerInterceptor），
        // 不能"selectList 全捞出来再 subList"——那对一个十万条的会话就是一次 OOM。
        // searchCount=false：增量同步每 2 秒一次，不该为此多跑一条 COUNT(*)。
        int cap = clampSize(size);
        Page<ImMessageDO> page = new Page<ImMessageDO>(1L, cap);
        page.setSearchCount(false);
        List<ImMessageDO> rows = messageMapper.selectPage(page, qw).getRecords();
        return rows == null ? Collections.<ImMessageDO>emptyList() : rows;
    }

    /**
     * 按 seq 取一条（客户端点"回复"气泡时反查原文）。
     */
    public ImMessageDO bySeq(Long conversationId, Long me, long seq) {
        conversationService.requireMember(conversationId, me);
        LambdaQueryWrapper<ImMessageDO> qw = new LambdaQueryWrapper<ImMessageDO>();
        qw.eq(ImMessageDO::getConversationId, conversationId).eq(ImMessageDO::getSeq, seq);
        ImMessageDO row = messageMapper.selectOne(qw);
        if (row == null) {
            return null;
        }
        ImMemberDO member = conversationService.requireMember(conversationId, me);
        long cleared = member.getClearedSeq() == null ? 0L : member.getClearedSeq().longValue();
        return seq <= cleared ? null : row;
    }

    /**
     * 清空会话：把本人的 {@code cleared_seq} 推到会话当前水位。返回推到的位置。
     * 只前进不回退，且不动 {@code last_read_seq}（未读数因此保持 0，清空不会"凭空多出未读"）。
     */
    public long clear(Long conversationId, Long me) {
        conversationService.requireMember(conversationId, me);
        ImConversationDO conv = conversationService.requireConversation(conversationId);
        long head = conv.getLastMsgSeq() == null ? 0L : conv.getLastMsgSeq().longValue();
        int hit = memberMapper.advanceClearedSeq(conversationId, me, head);
        if (hit == 0) {
            // cleared_seq 已经在更靠后的位置（例如清空后又有人建了同 key 的请求）：幂等，回读真实值
            ImMemberDO member = conversationService.requireMember(conversationId, me);
            return member.getClearedSeq() == null ? 0L : member.getClearedSeq().longValue();
        }
        return head;
    }

    /**
     * 会话当前水位（{@code last_msg_seq}）。成员才拿得到。
     * 客户端订上 {@code room:} 之前先读一次它，之后按 {@code seq > head} 的帧增量接，
     * 不必为了补"订阅前那几帧"去拉一次全量历史。
     */
    public long headSeq(Long conversationId, Long me) {
        conversationService.requireMember(conversationId, me);
        ImConversationDO conv = conversationService.requireConversation(conversationId);
        return conv.getLastMsgSeq() == null ? 0L : conv.getLastMsgSeq().longValue();
    }

    // ---------------------------------------------------------------- 投递

    /**
     * 一帧两投：{@code room:<id>} 给订了会话的连接，{@code user:<id>} 给只订自己收件箱的人。
     * 整段吞掉异常：实时失败不影响落库结果。
     */
    private void publishFrame(ImConversationDO conv, ImMessageDO message, LocalDateTime when) {
        if (!properties.isPublishRealtime()) {
            return;
        }
        RealtimePublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            return;
        }
        Map<String, Object> payload = payloadOf(message, when);
        String topic = RealtimeTopics.room(conv.getId());
        try {
            publisher.publish(topic, RealtimeMessage.KIND_CHAT, payload, message.getSeq().longValue());
        } catch (RuntimeException e) {
            log.warn("[z-msg-im] room 帧投递失败（不影响落库）topic={} err={}", topic, e.toString());
        }
        if (!properties.isUserSidePush()) {
            return;
        }
        for (Long peer : sidePushTargets(conv, message.getSenderUserId())) {
            try {
                publisher.publish(RealtimeTopics.user(peer), RealtimeMessage.KIND_CHAT, payload,
                        message.getSeq().longValue());
            } catch (RuntimeException e) {
                log.warn("[z-msg-im] user 帧投递失败（不影响落库）user={} err={}", peer, e.toString());
            }
        }
    }

    /**
     * {@code user:} 侧要投给谁：会话内除发送者外的人。
     * 单聊就是对面那一个；GROUP 超过 {@code z-msg.im.user-side-push-max-members} 就不扇
     * （大房间里一条消息打 N 次投递是纯粹的自伤，那种会话只该走 {@code room:}）；
     * ROOM 一律不扇。
     */
    private List<Long> sidePushTargets(ImConversationDO conv, Long senderUserId) {
        if (ImConvTypes.isSingle(conv.getConvType())) {
            Long peer = ImRoles.peerOfSingle(conv.getUkPair(), senderUserId);
            return peer == null ? Collections.<Long>emptyList() : Collections.singletonList(peer);
        }
        if (!ImConvTypes.GROUP.equals(conv.getConvType())) {
            return Collections.emptyList();
        }
        int count = conv.getMemberCount() == null ? 0 : conv.getMemberCount().intValue();
        if (count > properties.getUserSidePushMaxMembers()) {
            if (log.isDebugEnabled()) {
                log.debug("[z-msg-im] 会话 {} 成员 {} 人超过 user-side-push-max-members={}，跳过 user: 扇出",
                        conv.getId(), count, properties.getUserSidePushMaxMembers());
            }
            return Collections.emptyList();
        }
        LambdaQueryWrapper<ImMemberDO> qw = new LambdaQueryWrapper<ImMemberDO>();
        qw.eq(ImMemberDO::getConversationId, conv.getId());
        List<ImMemberDO> members = memberMapper.selectList(qw);
        List<Long> targets = new ArrayList<Long>();
        if (members != null) {
            for (ImMemberDO m : members) {
                if (m.getUserId() != null && !m.getUserId().equals(senderUserId)) {
                    targets.add(m.getUserId());
                }
            }
        }
        return targets;
    }

    /**
     * 帧的 payload，形状见 {@code _doc/001_WS_PROTOCOL.md} §2：外层帧的
     * {@code op=message / kind=chat / topic / seq / ts} 由 {@code RealtimeMessage} 负责，
     * 这里只给 payload 本体。时间是 epoch 毫秒而不是 LocalDateTime ——
     * 对端不必为它配 JavaTime 模块。
     */
    private Map<String, Object> payloadOf(ImMessageDO message, LocalDateTime when) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", message.getId());
        payload.put("conversationId", message.getConversationId());
        payload.put("seq", message.getSeq());
        payload.put("senderUserId", message.getSenderUserId());
        payload.put("msgType", message.getMsgType());
        payload.put("content", message.getContent());
        if (message.getClientMsgId() != null) {
            payload.put("clientMsgId", message.getClientMsgId());
        }
        if (message.getAtUserIds() != null) {
            payload.put("atUserIds", message.getAtUserIds());
        }
        if (message.getReplyToSeq() != null) {
            payload.put("replyToSeq", message.getReplyToSeq());
        }
        payload.put("createdTime", when == null ? System.currentTimeMillis()
                : when.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return payload;
    }

    /**
     * 已读回执帧（kind=read）：给群里"谁读到哪"的实时展示用，seq 用会话水位。
     * 与消息帧同一条容错规则：发不出去不影响游标已经写进库这个事实。
     */
    void publishReadFrame(Long conversationId, Long readerUserId, long lastReadSeq, long headSeq) {
        if (!properties.isPublishRealtime() || !properties.isPublishReadReceipt()) {
            return;
        }
        RealtimePublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("conversationId", conversationId);
        payload.put("userId", readerUserId);
        payload.put("lastReadSeq", lastReadSeq);
        try {
            publisher.publish(RealtimeTopics.room(conversationId), RealtimeMessage.KIND_READ,
                    payload, headSeq);
        } catch (RuntimeException e) {
            log.warn("[z-msg-im] read 帧投递失败 conversation={} err={}", conversationId, e.toString());
        }
    }

    /**
     * 会话列表用的摘要：非文本消息只放一个占位标签，长文本截到
     * {@code z_msg_im_conversation.last_msg_preview} 放得下的长度。
     */
    public String previewOf(String msgType, String content) {
        String text;
        if ("IMAGE".equals(msgType)) {
            text = "[图片]";
        } else if ("FILE".equals(msgType)) {
            text = "[文件]";
        } else if ("AUDIO".equals(msgType)) {
            text = "[语音]";
        } else {
            text = content == null ? "" : content.replace('\n', ' ').trim();
        }
        if (text.isEmpty()) {
            return msgType;
        }
        return text.length() <= PREVIEW_MAX_CHARS ? text : text.substring(0, PREVIEW_MAX_CHARS);
    }

    private ImMessageDO findByClientMsgId(Long conversationId, String clientMsgId) {
        LambdaQueryWrapper<ImMessageDO> qw = new LambdaQueryWrapper<ImMessageDO>();
        qw.eq(ImMessageDO::getConversationId, conversationId).eq(ImMessageDO::getClientMsgId, clientMsgId);
        return messageMapper.selectOne(qw);
    }

    private String normalizeMsgType(String msgType) {
        if (msgType == null || msgType.trim().isEmpty()) {
            return "TEXT";
        }
        String t = msgType.trim().toUpperCase();
        if (!MSG_TYPES.contains(t)) {
            throw new IllegalArgumentException("不支持的 msgType: " + msgType + "，取值 " + MSG_TYPES);
        }
        return t;
    }

    private String validateContent(String content, String msgType) {
        if ("SYS".equals(msgType)) {
            throw new IllegalArgumentException("SYS 消息只能由服务端内部产生，不接受接口调用方提交");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("content 不能为空");
        }
        if (properties.getMaxContentLength() > 0
                && content.length() > properties.getMaxContentLength()) {
            throw new IllegalArgumentException("content 超过 z-msg.im.max-content-length="
                    + properties.getMaxContentLength() + "，当前 " + content.length());
        }
        return content;
    }

    private int clampSize(int size) {
        int s = size <= 0 ? properties.getDefaultPageSize() : size;
        return Math.min(s, Math.max(1, properties.getMaxPageSize()));
    }
}
