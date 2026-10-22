package com.zifang.z.msg.web.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * {@code POST /api/msg/send} 的请求体 —— {@code Message} 的<b>直填子集</b>。
 * <p>
 * 只收「调用方自己能决定」的那几个字段（渠道 / 收件人 / 归属用户 / 标题 / 正文 / 跳转链接 /
 * 业务类型 / 幂等键），刻意不收模板参数、优先级、多语言、供应商扩展位这些"由服务端策略决定"的字段：
 * 直发端点要的是一行透传，不是让调用方把 {@code Message} 的所有旋钮都拧一遍。
 * <p>
 * 字段名与 {@code com.zifang.z.msg.api.Message} 同名同义，Jackson 直接按同名映射，
 * 不引入第二套命名（改名要改两处的那种坑）。
 * <p>
 * 未知字段一律忽略（{@code ignoreUnknown}）：调用方多塞一个字段不该让整条消息发不出去。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsgSendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 通道，取值见 {@link com.zifang.z.msg.api.Channels}（大小写不敏感，服务端会归一化成大写）。
     */
    private String channel;

    /**
     * 接收方：手机号 / 邮箱 / userId / webhook url / 设备 token，语义由 channel 决定。
     */
    private String receiver;

    /**
     * 归属用户 id（站内信落库、偏好过滤、按用户限流用）。
     */
    private Long userId;

    /**
     * 已渲染好的标题；为空时服务端退回业务类型。
     */
    private String subject;

    /**
     * 已渲染好的正文（必填）。
     */
    private String content;

    /**
     * 点击跳转链接。
     */
    private String linkUrl;

    /**
     * 业务类型（REGISTER / ORDER_PAID / SECURITY_ALERT ...）。为空时按 {@code DIRECT_SEND} 归档。
     */
    private String bizType;

    /**
     * 幂等键：同 channel + bizType + idempotencyKey 只投递一次。
     */
    private String idempotencyKey;

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
}
