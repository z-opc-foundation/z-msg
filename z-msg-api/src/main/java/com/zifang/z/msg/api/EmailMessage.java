package com.zifang.z.msg.api;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Map;

public class EmailMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    @Email
    private String to;

    @NotBlank
    private String bizType;

    private Map<String, String> templateParams;

    private String subject;

    public EmailMessage() {
    }

    public EmailMessage(String to, String bizType, Map<String, String> templateParams, String subject) {
        this.to = to;
        this.bizType = bizType;
        this.templateParams = templateParams;
        this.subject = subject;
    }

    public static EmailMessageBuilder builder() {
        return new EmailMessageBuilder();
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Map<String, String> getTemplateParams() {
        return templateParams;
    }

    public void setTemplateParams(Map<String, String> templateParams) {
        this.templateParams = templateParams;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public static class EmailMessageBuilder {
        private String to;
        private String bizType;
        private Map<String, String> templateParams;
        private String subject;

        public EmailMessageBuilder to(String v) {
            this.to = v;
            return this;
        }

        public EmailMessageBuilder bizType(String v) {
            this.bizType = v;
            return this;
        }

        public EmailMessageBuilder templateParams(Map<String, String> v) {
            this.templateParams = v;
            return this;
        }

        public EmailMessageBuilder subject(String v) {
            this.subject = v;
            return this;
        }

        public EmailMessage build() {
            return new EmailMessage(to, bizType, templateParams, subject);
        }
    }
}
