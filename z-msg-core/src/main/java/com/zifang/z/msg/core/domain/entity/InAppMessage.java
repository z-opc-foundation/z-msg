package com.zifang.z.msg.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("z_msg_message")
public class InAppMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String eventType;
    private String title;
    private String content;
    private Integer isRead;
    /**
     * 幂等键(同一 bizType+user+key 不重复入库)
     */
    private String dedupKey;
    /**
     * 消息跳转链接
     */
    private String linkUrl;
    private String tenantCode;
    private String domainCode;
    private LocalDateTime createdTime;

    /**
     * 1.1.0 全链路消息 id（与投递日志、实时帧共用一个标识，前端据此去重与已读回执）
     */
    private String msgId;
    /**
     * 1.1.0 消息大类：NOTICE 通知 / TODO 待办 / ALERT 告警 / PROMO 运营，前端按类分 tab
     */
    private String msgType;
    /**
     * 1.1.0 优先级，0 普通 1 重要 2 紧急，与 {@code Message.priority} 对齐
     */
    private Integer priority;
    /**
     * 1.1.0 过期时间，为空永不过期；列表查询会过滤已过期项
     */
    private LocalDateTime expireAt;
    /**
     * 1.1.0 是否置顶
     */
    private Integer pinned;
    /**
     * 1.1.0 已读时间，未读为 null（isRead 仍保留以便老代码继续用 0/1 判断）
     */
    private LocalDateTime readTime;
    /**
     * 1.1.0 逻辑删除，列表默认过滤 deleted=0
     */
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public Integer getPinned() {
        return pinned;
    }

    public void setPinned(Integer pinned) {
        this.pinned = pinned;
    }

    public LocalDateTime getReadTime() {
        return readTime;
    }

    public void setReadTime(LocalDateTime readTime) {
        this.readTime = readTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getIsRead() {
        return isRead;
    }

    public void setIsRead(Integer isRead) {
        this.isRead = isRead;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
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
}
