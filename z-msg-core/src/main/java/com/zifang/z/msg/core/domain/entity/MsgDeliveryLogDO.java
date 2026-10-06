package com.zifang.z.msg.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 投递日志 DO
 * <p>
 * Phase 2 引入:每次外发通道 (sms/email/webhook/im/push) 的发送结果都落一行,用于状态查询 / 统计 / 重试。
 */
@TableName("z_msg_delivery_log")
public class MsgDeliveryLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    /**
     * SMS / EMAIL / WEBHOOK / IM / PUSH / IN_APP
     */
    private String channel;
    /**
     * 出站幂等位。存的是 {@code channel|bizType|key|userId|receiver} 拼成的去重令牌
     * （拼法见 {@code ChannelRouter.dedupToken}），不是调用方传进来的裸 key ——
     * 裸 key 单独不唯一：同一 dedupKey 可能发给多个用户。
     *
     * <p><b>只有"已送达"的行持有它</b>：厂商故障、限流、偏好屏蔽、静默时段一律写 NULL。
     * 唯一索引允许多个 NULL，所以不占位的行彼此不会撞；这一条与
     * {@code ChannelRouter} 里"没送出去就不占位"的判断必须成对维护。</p>
     */
    private String idempotencyKey;
    /**
     * 实际 provider (mock/smtp/aliyun/wecom...)
     */
    private String provider;
    /**
     * 接收方 (手机号 / 邮箱 / url / userId)
     */
    private String receiver;
    private Long userId;
    private Long templateId;
    private String renderedSubject;
    private String renderedContent;
    private String providerMessageId;
    /**
     * 0=pending 1=success 2=failed 3=retrying
     */
    private Integer status;
    private String errorCode;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetry;
    private LocalDateTime nextRetryTime;
    private Integer durationMs;
    private String tenantCode;
    private String domainCode;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
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

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public String getRenderedSubject() {
        return renderedSubject;
    }

    public void setRenderedSubject(String renderedSubject) {
        this.renderedSubject = renderedSubject;
    }

    public String getRenderedContent() {
        return renderedContent;
    }

    public void setRenderedContent(String renderedContent) {
        this.renderedContent = renderedContent;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(Integer maxRetry) {
        this.maxRetry = maxRetry;
    }

    public LocalDateTime getNextRetryTime() {
        return nextRetryTime;
    }

    public void setNextRetryTime(LocalDateTime nextRetryTime) {
        this.nextRetryTime = nextRetryTime;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
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

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }
}
