package com.zifang.z.msg.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

import java.time.LocalDateTime;

/**
 * 用户渠道偏好 DO
 * <p>
 * Phase 2 引入:每用户 × 每 bizType 配置可接收的渠道白名单 + 静默时段。
 * bizType="*" 表示全局默认。
 */
@BizModel(name = "用户渠道偏好 DO", module = "z-msg", description = "用户渠道偏好 DO Phase 2 引入:每用户 × 每 bizType 配置可接收的渠道白名单 + 静默时段。 bizType=\"*\" 表示全局默认。")
@TableName("z_msg_user_preference")
public class MsgUserPreferenceDO {

    @TableId(type = IdType.AUTO)
    @BizField(name = "id", type = BizField.FieldType.INTEGER)
    private Long id;
    @BizField(name = "userId", type = BizField.FieldType.INTEGER)
    private Long userId;
    /**
     * 业务类型, * 表示全局默认
     */
    @BizField(name = "业务类型, * 表示全局默认", type = BizField.FieldType.STRING)
    private String bizType;
    /**
     * 允许的渠道列表,逗号分隔 (SMS,EMAIL,IN_APP,WEBHOOK,IM_WECOM,...),空表示全部
     */
    @BizField(name = "允许渠道，逗号分隔；空=全部", type = BizField.FieldType.STRING)
    private String channels;
    /**
     * 静默开始 HH:mm,在此期间不发送非紧急消息
     */
    @BizField(name = "HH:mm，静默时段起", type = BizField.FieldType.STRING)
    private String quietStart;
    /**
     * 静默结束 HH:mm
     */
    @BizField(name = "HH:mm，静默时段止", type = BizField.FieldType.STRING)
    private String quietEnd;
    @BizField(name = "同样不能留 NULL，见 z_msg_template.tenant_code", type = BizField.FieldType.STRING)
    private String tenantCode;
    @BizField(name = "domainCode", type = BizField.FieldType.STRING)
    private String domainCode;
    @BizField(name = "createdTime", type = BizField.FieldType.DATETIME)
    private LocalDateTime createdTime;
    @BizField(name = "updatedTime", type = BizField.FieldType.DATETIME)
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



