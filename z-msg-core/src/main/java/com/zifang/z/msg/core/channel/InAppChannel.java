package com.zifang.z.msg.core.channel;

import com.zifang.z.msg.api.MessageBus.MessageHandler;
import com.zifang.z.msg.api.MessageEvent;
import com.zifang.z.msg.core.domain.entity.InAppMessage;
import com.zifang.z.msg.core.domain.mapper.InAppMessageMapper;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 站内消息通道 (Phase 1.4 改造: content 改为 JSON 序列化)
 * <p>
 * 接收 MessageEvent → 渲染模板 → 落库 z_msg_message
 */
public class InAppChannel implements MessageHandler {

    private static final Logger log = LogManager.getLogger(InAppChannel.class);

    private final InAppMessageMapper mapper;
    private final MessageTemplateEngine templateEngine;

    public InAppChannel(InAppMessageMapper mapper, MessageTemplateEngine templateEngine) {
        this.mapper = mapper;
        this.templateEngine = templateEngine;
    }

    @Override
    public void handle(MessageEvent event) {
        Map<String, Object> params = event.getParams();
        // Phase 1.4: content 渲染为 JSON (模板渲染结果),而不是 Map.toString()
        String renderedContent = templateEngine.render(event.getEventType(), "IN_APP", toStringMap(params));
        String renderedTitle = pickTitle(event.getEventType(), params);

        // Phase 3: 幂等性检查 — 同一 dedupKey 不重复入库
        String dedupKey = event.getDedupKey();
        if (dedupKey != null && !dedupKey.isEmpty()) {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InAppMessage> qw =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            qw.eq(InAppMessage::getDedupKey, dedupKey).last("LIMIT 1");
            if (mapper.selectOne(qw) != null) {
                log.debug("[InAppChannel] dedup skip: dedupKey={}", dedupKey);
                return;
            }
        }

        InAppMessage msg = new InAppMessage();
        msg.setUserId(event.getUserId());
        msg.setEventType(event.getEventType());
        msg.setTitle(renderedTitle);
        msg.setContent(renderedContent);
        msg.setLinkUrl(event.getLinkUrl());
        msg.setDedupKey(dedupKey);
        msg.setIsRead(0);
        msg.setCreatedTime(LocalDateTime.now());
        if (params != null && params.get("tenantCode") instanceof String) {
            msg.setTenantCode((String) params.get("tenantCode"));
        }
        if (params != null && params.get("domainCode") instanceof String) {
            msg.setDomainCode((String) params.get("domainCode"));
        }
        mapper.insert(msg);
        log.debug("[InAppChannel] eventType={} userId={} title={} dedupKey={}",
                event.getEventType(), event.getUserId(), renderedTitle, dedupKey);
    }

    private String pickTitle(String bizType, Map<String, Object> params) {
        MessageTemplateEngine.TemplateEntry t = templateEngine.lookup(bizType, "IN_APP", null);
        if (t != null && t.subject != null && !t.subject.isEmpty()) {
            return render(t.subject, params);
        }
        return bizType;
    }

    private String render(String template, Map<String, Object> params) {
        if (template == null) {
            return "";
        }

        if (params == null || params.isEmpty()) {
            return template;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\$\\{(\\w+)}").matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            Object value = params.get(key);
            String s = value == null ? "" : value.toString();
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(s));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private Map<String, String> toStringMap(Map<String, Object> params) {
        if (params == null) {
            return null;
        }

        java.util.Map<String, String> out = new java.util.HashMap<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            out.put(e.getKey(), e.getValue() == null ? null : e.getValue().toString());
        }
        return out;
    }
}
