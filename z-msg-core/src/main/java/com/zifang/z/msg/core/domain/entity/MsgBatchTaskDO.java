package com.zifang.z.msg.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

import java.time.LocalDateTime;

/**
 * 批量发送任务 DO
 * <p>
 * Phase 2 引入:异步批量发送 + 进度查询。
 */
@BizModel(name = "批量发送任务 DO", module = "z-msg", description = "批量发送任务 DO Phase 2 引入:异步批量发送 + 进度查询。")
@TableName("z_msg_batch_task")
public class MsgBatchTaskDO {

    @TableId(type = IdType.AUTO)
    @BizField(name = "id", type = BizField.FieldType.INTEGER)
    private Long id;
    @BizField(name = "bizType", type = BizField.FieldType.STRING)
    private String bizType;
    @BizField(name = "channel", type = BizField.FieldType.STRING)
    private String channel;
    @BizField(name = "templateId", type = BizField.FieldType.INTEGER)
    private Long templateId;
    @BizField(name = "totalCount", type = BizField.FieldType.INTEGER)
    private Integer totalCount;
    @BizField(name = "successCount", type = BizField.FieldType.INTEGER)
    private Integer successCount;
    @BizField(name = "failedCount", type = BizField.FieldType.INTEGER)
    private Integer failedCount;
    /**
     * 0=pending 1=running 2=done 3=partial-failed 4=failed
     */
    @BizField(name = "0=pending 1=running 2=done 3=partial-failed 4=failed 5=cancelled", type = BizField.FieldType.INTEGER)
    private Integer status;
    /**
     * 所有接收方共用的渲染参数 (JSON)
     */
    @BizField(name = "所有接收方共用的渲染参数 (JSON)", type = BizField.FieldType.STRING)
    private String paramsJson;
    /**
     * 接收方列表 [{"receiver":"138...","params":{...}}]
     */
    @BizField(name = "收件人列表快照；万人级活动不能塞进 VARCHAR", type = BizField.FieldType.STRING)
    private String receiversJson;
    @BizField(name = "errorMessage", type = BizField.FieldType.STRING)
    private String errorMessage;
    @BizField(name = "tenantCode", type = BizField.FieldType.STRING)
    private String tenantCode;
    @BizField(name = "domainCode", type = BizField.FieldType.STRING)
    private String domainCode;
    @BizField(name = "createdTime", type = BizField.FieldType.DATETIME)
    private LocalDateTime createdTime;
    @BizField(name = "updatedTime", type = BizField.FieldType.DATETIME)
    private LocalDateTime updatedTime;
    @BizField(name = "createdBy", type = BizField.FieldType.STRING)
    private String createdBy;
    @BizField(name = "finishedTime", type = BizField.FieldType.DATETIME)
    private LocalDateTime finishedTime;

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

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public void setParamsJson(String paramsJson) {
        this.paramsJson = paramsJson;
    }

    public String getReceiversJson() {
        return receiversJson;
    }

    public void setReceiversJson(String receiversJson) {
        this.receiversJson = receiversJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public LocalDateTime getFinishedTime() {
        return finishedTime;
    }

    public void setFinishedTime(LocalDateTime finishedTime) {
        this.finishedTime = finishedTime;
    }
}



