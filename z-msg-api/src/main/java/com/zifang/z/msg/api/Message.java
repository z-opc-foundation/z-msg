package com.zifang.z.msg.api;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一消息信封 (1.1.0)
 * <p>
 * 所有通道 (SMS / EMAIL / IN_APP / IM_* / PUSH_* / WEBHOOK) 的发送入参收敛到本类，
 * 业务侧不再为每个通道写一个 DTO。{@link ChannelSender} 实现按 channel 取自己关心的字段。
 * <p>
 * 与遗留的 {@link SmsMessage}/{@link EmailMessage} 并存：后者仍可单独使用，
 * {@link MessageGateway} 内部会转成本类再路由。
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 通道常量，取值见 {@link Channels}
     */
    private String channel;
    /**
     * 接收方：手机号 / 邮箱 / userId / webhook url / 设备 token，语义由 channel 决定
     */
    private String receiver;
    /**
     * 归属用户 id（站内信、偏好过滤、按用户限流用）
     */
    private Long userId;
    /**
     * 业务类型 (REGISTER / ORDER_PAID / SECURITY_ALERT ...)
     */
    private String bizType;
    /**
     * 消息大类，站内信分 tab 用：NOTICE / TODO / ALERT / PROMO
     */
    private String msgType;
    /**
     * 模板 code；为空时 router 按 bizType + channel 查模板
     */
    private String templateCode;
    /**
     * 渲染参数
     */
    private Map<String, String> params = new HashMap<>();
    /**
     * 已渲染好的标题；非空则跳过模板渲染
     */
    private String subject;
    /**
     * 已渲染好的正文；非空则跳过模板渲染
     */
    private String content;
    private String linkUrl;
    /**
     * 多语言 (zh_CN / en_US)
     */
    private String locale;
    /**
     * 幂等键：同 channel + bizType + idempotencyKey 只投递一次
     */
    private String idempotencyKey;
    /**
     * 优先级：0 普通 / 1 重要（跳过静默时段）/ 2 紧急（跳过静默时段 + 限流）
     */
    private int priority = PRIORITY_NORMAL;

    public static final int PRIORITY_NORMAL = 0;
    public static final int PRIORITY_IMPORTANT = 1;
    public static final int PRIORITY_URGENT = 2;

    private String tenantCode;
    private String domainCode;
    /**
     * 过期时间；站内信读取时过滤过期项
     */
    private LocalDateTime expireAt;
    /**
     * 全链路消息 id，默认自动生成，投递日志与实时下发共用
     */
    private String msgId = UUID.randomUUID().toString().replace("-", "");
    /**
     * 供应商扩展位（如钉钉的 atMobiles、企微的 agentId 覆盖）
     */
    private Map<String, Object> vendorOptions = new HashMap<>();

    public Message() {
    }

    public Message(String channel, String receiver) {
        this.channel = channel;
        this.receiver = receiver;
    }

    public static MessageBuilder builder() {
        return new MessageBuilder();
    }

    public Message copyForChannel(String newChannel, String newReceiver) {
        Message c = new Message();
        c.channel = newChannel;
        c.receiver = newReceiver;
        c.userId = this.userId;
        c.bizType = this.bizType;
        c.msgType = this.msgType;
        c.templateCode = this.templateCode;
        c.params = this.params;
        c.subject = this.subject;
        c.content = this.content;
        c.linkUrl = this.linkUrl;
        c.locale = this.locale;
        c.idempotencyKey = this.idempotencyKey;
        c.priority = this.priority;
        c.tenantCode = this.tenantCode;
        c.domainCode = this.domainCode;
        c.expireAt = this.expireAt;
        c.vendorOptions = this.vendorOptions;
        return c;
    }

    public boolean isUrgent() {
        return priority >= PRIORITY_IMPORTANT;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params == null ? new HashMap<String, String>() : params;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getDomainCode() {
        return domainCode;
    }

    public void setDomainCode(String domainCode) {
        this.domainCode = domainCode;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public Map<String, Object> getVendorOptions() {
        return vendorOptions;
    }

    public void setVendorOptions(Map<String, Object> vendorOptions) {
        this.vendorOptions = vendorOptions == null ? new HashMap<String, Object>() : vendorOptions;
    }

    public String param(String key) {
        return params == null ? null : params.get(key);
    }

    public String param(String key, String defaultValue) {
        String v = param(key);
        return v == null ? defaultValue : v;
    }

    @Override
    public String toString() {
        return "Message{channel='" + channel + "', receiver='" + receiver + "', bizType='" + bizType
                + "', userId=" + userId + ", msgId='" + msgId + "'}";
    }

    public static class MessageBuilder {
        private final Message m = new Message();

        public MessageBuilder channel(String v) {
            m.channel = v;
            return this;
        }

        public MessageBuilder receiver(String v) {
            m.receiver = v;
            return this;
        }

        public MessageBuilder userId(Long v) {
            m.userId = v;
            return this;
        }

        public MessageBuilder bizType(String v) {
            m.bizType = v;
            return this;
        }

        public MessageBuilder msgType(String v) {
            m.msgType = v;
            return this;
        }

        public MessageBuilder templateCode(String v) {
            m.templateCode = v;
            return this;
        }

        public MessageBuilder params(Map<String, String> v) {
            m.setParams(v);
            return this;
        }

        public MessageBuilder param(String k, String v) {
            m.params.put(k, v);
            return this;
        }

        public MessageBuilder subject(String v) {
            m.subject = v;
            return this;
        }

        public MessageBuilder content(String v) {
            m.content = v;
            return this;
        }

        public MessageBuilder linkUrl(String v) {
            m.linkUrl = v;
            return this;
        }

        public MessageBuilder locale(String v) {
            m.locale = v;
            return this;
        }

        public MessageBuilder idempotencyKey(String v) {
            m.idempotencyKey = v;
            return this;
        }

        public MessageBuilder priority(int v) {
            m.priority = v;
            return this;
        }

        public MessageBuilder tenantCode(String v) {
            m.tenantCode = v;
            return this;
        }

        public MessageBuilder domainCode(String v) {
            m.domainCode = v;
            return this;
        }

        public MessageBuilder expireAt(LocalDateTime v) {
            m.expireAt = v;
            return this;
        }

        public MessageBuilder msgId(String v) {
            m.msgId = v;
            return this;
        }

        public MessageBuilder vendorOption(String k, Object v) {
            m.vendorOptions.put(k, v);
            return this;
        }

        public Message build() {
            return m;
        }
    }
}
