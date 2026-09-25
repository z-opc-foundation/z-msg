package com.zifang.z.msg.core.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageBus.MessageHandler;
import com.zifang.z.msg.api.MessageEvent;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.api.RealtimeMessage;
import com.zifang.z.msg.api.RealtimePublisher;
import com.zifang.z.msg.core.config.MessageProperties;
import com.zifang.z.msg.core.domain.entity.InAppMessage;
import com.zifang.z.msg.core.domain.mapper.InAppMessageMapper;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 站内消息通道 (1.1.0)
 * <p>
 * 1.0.0 里它只是 MessageBus 的一个全局 handler，导致两个问题：
 * <ul>
 *   <li>{@code ChannelRouter.dispatch()} 的 switch 里没有 IN_APP 分支，
 *       批量任务的默认通道 {@code IN_APP} 永远返回 UNSUPPORTED_CHANNEL；</li>
 *   <li>落库后不推实时，前端只能轮询 {@code /api/msg/unread}。</li>
 * </ul>
 * 现在它同时是 {@code Channels.IN_APP} 的 provider（provider="db"），
 * 与其他通道走同一条 dispatch 路径，写库成功后按配置推 {@code user:<id>} topic。
 */
public class InAppChannel implements MessageHandler, ChannelSender {

    private static final Logger log = LogManager.getLogger(InAppChannel.class);

    /**
     * 站内信写库这一 provider 的固定名字
     */
    public static final String PROVIDER = "db";

    private final InAppMessageMapper mapper;
    private final MessageTemplateEngine templateEngine;
    private final MessageProperties properties;
    /**
     * 可选：宿主没带 z-msg-ws 时只有落库，不实时推，功能不报错
     */
    private volatile RealtimePublisher publisher;

    public InAppChannel(InAppMessageMapper mapper, MessageTemplateEngine templateEngine) {
        this(mapper, templateEngine, null, null);
    }

    public InAppChannel(InAppMessageMapper mapper, MessageTemplateEngine templateEngine,
                        MessageProperties properties, RealtimePublisher publisher) {
        this.mapper = mapper;
        this.templateEngine = templateEngine;
        this.properties = properties;
        this.publisher = publisher;
    }

    public void setPublisher(RealtimePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public String channel() {
        return Channels.IN_APP;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public void handle(MessageEvent event) {
        Message m = new Message();
        m.setChannel(Channels.IN_APP);
        m.setUserId(event.getUserId());
        m.setReceiver(event.getUserId() == null ? null : String.valueOf(event.getUserId()));
        m.setBizType(event.getEventType());
        m.setLinkUrl(event.getLinkUrl());
        m.setIdempotencyKey(event.getDedupKey());
        m.setParams(toStringMap(event.getParams()));
        Map<String, Object> p = event.getParams();
        if (p != null) {
            Object t = p.get("tenantCode");
            Object d = p.get("domainCode");
            Object type = p.get("msgType");
            if (t instanceof String) {
                m.setTenantCode((String) t);
            }
            if (d instanceof String) {
                m.setDomainCode((String) d);
            }
            if (type instanceof String) {
                m.setMsgType((String) type);
            }
        }
        send(m);
    }

    @Override
    public MessageSendResult send(Message message) {
        if (message.getUserId() == null) {
            return MessageSendResult.fail(PROVIDER, "INVALID_RECEIVER", "站内信必须有 userId");
        }
        String dedupKey = message.getIdempotencyKey();
        if (dedupKey != null && !dedupKey.isEmpty() && alreadyExists(message, dedupKey)) {
            log.debug("[InAppChannel] dedup skip: dedupKey={}", dedupKey);
            return MessageSendResult.ok(PROVIDER, "DEDUP-" + dedupKey);
        }

        MessageTemplateEngine.RenderedTemplate rendered = renderTitleAndBody(message);

        InAppMessage row = new InAppMessage();
        row.setUserId(message.getUserId());
        row.setEventType(message.getBizType());
        row.setTitle(rendered.getSubject());
        row.setContent(rendered.getContent());
        row.setLinkUrl(message.getLinkUrl());
        row.setDedupKey(dedupKey);
        row.setMsgId(message.getMsgId());
        row.setMsgType(resolveMsgType(message));
        row.setPriority(message.getPriority());
        row.setPinned(0);
        row.setIsRead(0);
        row.setDeleted(0);
        row.setTenantCode(message.getTenantCode());
        row.setDomainCode(message.getDomainCode());
        row.setExpireAt(resolveExpireAt(message));
        row.setCreatedTime(LocalDateTime.now());
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException e) {
            // alreadyExists() 的 select 与 insert 之间有窗口：并发重复投递由
            // uk_msg_user_dedup(user_id, dedup_key) 兜住，语义与上面的 dedup skip 保持一致。
            log.debug("[InAppChannel] dedup by unique index: dedupKey={}", dedupKey);
            return MessageSendResult.ok(PROVIDER, "DEDUP-" + dedupKey);
        }

        pushRealtime(message, row);
        log.debug("[InAppChannel] 入库 userId={} eventType={} msgId={}",
                row.getUserId(), row.getEventType(), row.getMsgId());
        return MessageSendResult.ok(PROVIDER, row.getMsgId());
    }

    /**
     * 幂等判定用 msgId 而不是 dedupKey 单列：同一 dedupKey 可能发给多个用户，
     * 只有 (userId, dedupKey) 组合才是"这一条站内信"。
     */
    private boolean alreadyExists(Message message, String dedupKey) {
        LambdaQueryWrapper<InAppMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(InAppMessage::getUserId, message.getUserId())
                .eq(InAppMessage::getDedupKey, dedupKey)
                .last("LIMIT 1");
        return mapper.selectOne(qw) != null;
    }

    private MessageTemplateEngine.RenderedTemplate renderTitleAndBody(Message message) {
        String subject = message.getSubject();
        String content = message.getContent();
        if (subject != null && !subject.isEmpty() && content != null && !content.isEmpty()) {
            return new MessageTemplateEngine.RenderedTemplate(subject, content);
        }
        MessageTemplateEngine.RenderedTemplate t = null;
        if (templateEngine != null) {
            try {
                t = templateEngine.renderWithLocale(message.getBizType(), Channels.IN_APP,
                        message.getLocale(), message.getParams());
            } catch (Exception e) {
                log.warn("[InAppChannel] 模板渲染失败 bizType={} err={}", message.getBizType(), e.toString());
            }
        }
        String title = subject != null && !subject.isEmpty()
                ? subject : (t == null ? message.getBizType() : t.getSubject());
        String body = content != null && !content.isEmpty()
                ? content : (t == null ? "" : t.getContent());
        return new MessageTemplateEngine.RenderedTemplate(
                title == null || title.isEmpty() ? message.getBizType() : title, body);
    }

    private String resolveMsgType(Message message) {
        String t = message.getMsgType();
        return t == null || t.isEmpty() ? "NOTICE" : t;
    }

    private LocalDateTime resolveExpireAt(Message message) {
        if (message.getExpireAt() != null) {
            return message.getExpireAt();
        }
        int days = properties == null ? 0 : properties.getInbox().getExpireDays();
        return days > 0 ? LocalDateTime.now().plusDays(days) : null;
    }

    private void pushRealtime(Message message, InAppMessage row) {
        RealtimePublisher p = this.publisher;
        if (p == null || (properties != null && !properties.getInbox().isPushRealtime())) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", row.getId());
            payload.put("msgId", row.getMsgId());
            payload.put("eventType", row.getEventType());
            payload.put("msgType", row.getMsgType());
            payload.put("title", row.getTitle());
            payload.put("content", row.getContent());
            payload.put("linkUrl", row.getLinkUrl());
            payload.put("priority", row.getPriority());
            payload.put("createdTime", row.getCreatedTime() == null ? null : row.getCreatedTime().toString());
            payload.put("unread", Boolean.TRUE);
            p.publish(com.zifang.z.msg.api.RealtimeTopics.user(row.getUserId()),
                    RealtimeMessage.KIND_INBOX, payload);
        } catch (Exception e) {
            // 实时推送失败不影响站内信本身已经落库这个事实
            log.warn("[InAppChannel] 实时推送失败 msgId={} err={}", row.getMsgId(), e.toString());
        }
    }

    private Map<String, String> toStringMap(Map<String, Object> params) {
        Map<String, String> out = new HashMap<>();
        if (params == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : params.entrySet()) {
            out.put(e.getKey(), e.getValue() == null ? null : e.getValue().toString());
        }
        return out;
    }
}
