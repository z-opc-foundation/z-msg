package com.zifang.z.msg.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户渠道偏好 DO
 * <p>
 * Phase 2 引入:每用户 × 每 bizType 配置可接收的渠道白名单 + 静默时段。
 * bizType="*" 表示全局默认。
 */
@TableName("z_msg_user_preference")
public class MsgUserPreferenceDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /**
     * 业务类型, * 表示全局默认
     */
    private String bizType;
    /**
     * 允许的渠道列表,逗号分隔 (SMS,EMAIL,IN_APP,WEBHOOK,IM_WECOM,...),空表示全部
     */
    private String channels;
    /**
     * 静默开始 HH:mm,在此期间不发送非紧急消息
     */
    private String quietStart;
    /**
     * 静默结束 HH:mm
     */
    private String quietEnd;
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

    public String getChannels() {
        return channels;
    }

    public void setChannels(String channels) {
        this.channels = channels;
    }

    public String getQuietStart() {
        return quietStart;
    }

    public void setQuietStart(String quietStart) {
        this.quietStart = quietStart;
    }

    public String getQuietEnd() {
        return quietEnd;
    }

    public void setQuietEnd(String quietEnd) {
        this.quietEnd = quietEnd;
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
