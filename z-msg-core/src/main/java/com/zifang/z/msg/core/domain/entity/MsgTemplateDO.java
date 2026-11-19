package com.zifang.z.msg.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

import java.time.LocalDateTime;

/**
 * 消息模板主表 DO
 * <p>
 * Phase 1 引入:模板 DB 化,支持前端 CRUD。
 * 优先级: DB > yml > 内置默认。
 */
@BizModel(name = "消息模板主表 DO", module = "z-msg", description = "消息模板主表 DO Phase 1 引入:模板 DB 化,支持前端 CRUD。 优先级: DB > yml > 内置默认。")
@TableName("z_msg_template")
public class MsgTemplateDO {

    @TableId(type = IdType.AUTO)
    @BizField(name = "id", type = BizField.FieldType.INTEGER)
    private Long id;

    /**
     * 业务类型 (REGISTER / LOGIN / RESET_PWD / ORDER_PAID ...)
     */
    @BizField(name = "业务类型 = Message.bizType / templateCode", type = BizField.FieldType.STRING)
    private String bizType;

    /**
     * 渠道 (SMS / EMAIL / IN_APP / WEBHOOK / IM_WECOM / IM_DINGTALK / IM_FEISHU / PUSH)
     */
    @BizField(name = "介质，取值见 Channels 常量", type = BizField.FieldType.STRING)
    private String channel;

    /**
     * 主题 (邮件 subject / 推送 title)
     */
    @BizField(name = "主题 (邮件 subject / 推送 title)", type = BizField.FieldType.STRING)
    private String subject;

    /**
     * 内容模板,支持 ${var} 占位符
     */
    @BizField(name = "内容模板,支持 ${var} 占位符", type = BizField.FieldType.STRING)
    private String content;

    /**
     * 状态: 0=草稿 1=启用 2=停用
     */
    @BizField(name = "0=草稿 1=启用 2=停用", type = BizField.FieldType.INTEGER)
    private Integer status;

    /**
     * 当前生效版本号 (Phase 3 模板版本化)
     */
    @BizField(name = "当前生效版本号 (Phase 3 模板版本化)", type = BizField.FieldType.INTEGER)
    private Integer version;

    @BizField(name = "空串而非 NULL：MySQL 唯一索引不约束 NULL，用 NULL 会让同一 (biz_type,channel) 重复建模板", type = BizField.FieldType.STRING)
    private String tenantCode;
    @BizField(name = "domainCode", type = BizField.FieldType.STRING)
    private String domainCode;
    @BizField(name = "createdTime", type = BizField.FieldType.DATETIME)
    private LocalDateTime createdTime;
    @BizField(name = "updatedTime", type = BizField.FieldType.DATETIME)
    private LocalDateTime updatedTime;
    @BizField(name = "createdBy", type = BizField.FieldType.STRING)
    private String createdBy;
    @BizField(name = "updatedBy", type = BizField.FieldType.STRING)
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



