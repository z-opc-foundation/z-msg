package com.zifang.z.msg.client.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 直发消息的请求体（对应服务端 {@code MsgSendRequest}）。
 * <p>
 * 语义与 {@code POST /api/msg/send} 一一对应：通道 + 收件人 + 运行时全文。
 * 站内信给 {@code userId}，短信/邮件给 {@code receiver}。
 */
public class ZMsgSendRequest {

    private String channel;
    private String receiver;
    private Long userId;
    private String subject;
    private String content;
    private String linkUrl;
    private String bizType;
    private String idempotencyKey;

    public ZMsgSendRequest() {
    }

    public ZMsgSendRequest(String channel, String content) {
        this.channel = channel;
        this.content = content;
    }

    public static ZMsgSendRequest inApp(Long userId, String subject, String content) {
        ZMsgSendRequest r = new ZMsgSendRequest("IN_APP", content);
        r.setUserId(userId);
        r.setSubject(subject);
        return r;
    }

    public static ZMsgSendRequest to(String channel, String receiver, String subject, String content) {
        ZMsgSendRequest r = new ZMsgSendRequest(channel, content);
        r.setReceiver(receiver);
        r.setSubject(subject);
        return r;
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

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * 转成请求体 Map（null 字段不带，服务端按缺省处理）。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        put(m, "channel", channel);
        put(m, "receiver", receiver);
        put(m, "userId", userId);
        put(m, "subject", subject);
        put(m, "content", content);
        put(m, "linkUrl", linkUrl);
        put(m, "bizType", bizType);
        put(m, "idempotencyKey", idempotencyKey);
        return m;
    }

    private static void put(Map<String, Object> m, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String && ((String) value).trim().isEmpty()) {
            return;
        }
        m.put(key, value);
    }
}
