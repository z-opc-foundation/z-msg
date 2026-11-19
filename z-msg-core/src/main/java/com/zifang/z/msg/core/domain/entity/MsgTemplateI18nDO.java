package com.zifang.z.msg.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

import java.time.LocalDateTime;

/**
 * 模板多语言 DO
 * <p>
 * Phase 3 引入:同一 bizType+channel 下, 按 locale 覆盖 subject/content。
 * lookup 顺序: i18n(locale) > template(content) > yml > 内置。
 */
@BizModel(name = "模板多语言 DO", module = "z-msg", description = "模板多语言 DO Phase 3 引入:同一 bizType+channel 下, 按 locale 覆盖 subject/content。 lookup 顺序: i18n(locale) > template(content) > yml > 内置。")
@TableName("z_msg_template_i18n")
public class MsgTemplateI18nDO {

    @TableId(type = IdType.AUTO)
    @BizField(name = "id", type = BizField.FieldType.INTEGER)
    private Long id;
    @BizField(name = "templateId", type = BizField.FieldType.INTEGER)
    private Long templateId;
    @BizField(name = "zh_CN / en_US …", type = BizField.FieldType.STRING)
    private String locale;
    @BizField(name = "subject", type = BizField.FieldType.STRING)
    private String subject;
    @BizField(name = "content", type = BizField.FieldType.STRING)
    private String content;
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

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
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



