package com.zifang.z.msg.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 消息模板主表 DO
 * <p>
 * Phase 1 引入:模板 DB 化,支持前端 CRUD。
 * 优先级: DB > yml > 内置默认。
 */
@TableName("z_msg_template")
public class MsgTemplateDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务类型 (REGISTER / LOGIN / RESET_PWD / ORDER_PAID ...)
     */
    private String bizType;

    /**
     * 渠道 (SMS / EMAIL / IN_APP / WEBHOOK / IM_WECOM / IM_DINGTALK / IM_FEISHU / PUSH)
     */
    private String channel;

    /**
     * 主题 (邮件 subject / 推送 title)
     */
    private String subject;

    /**
     * 内容模板,支持 ${var} 占位符
     */
    private String content;

    /**
     * 状态: 0=草稿 1=启用 2=停用
     */
    private Integer status;

    /**
     * 当前生效版本号 (Phase 3 模板版本化)
     */
    private Integer version;

    private String tenantCode;
    private String domainCode;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private String createdBy;
    private String updatedBy;

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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
