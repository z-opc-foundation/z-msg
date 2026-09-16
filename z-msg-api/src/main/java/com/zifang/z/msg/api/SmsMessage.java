package com.zifang.z.msg.api;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.Map;

/**
 * 短信消息 DTO
 *
 * @param phone          E.164 国际格式或国内 11 位（具体校验由 Provider 决定）
 * @param bizType        业务类型（REGISTER / LOGIN / RESET_PWD ...）
 * @param templateParams 模板参数（K-V），按短信商模板变量名填充
 * @param signName       短信签名（可选，为空时使用 z-meta 默认签名）
 */
public class SmsMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank
    private String bizType;

    private Map<String, String> templateParams;

    private String signName;

    public SmsMessage() {
    }

    public SmsMessage(String phone, String bizType, Map<String, String> templateParams, String signName) {
        this.phone = phone;
        this.bizType = bizType;
        this.templateParams = templateParams;
        this.signName = signName;
    }

    public static SmsMessageBuilder builder() {
        return new SmsMessageBuilder();
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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

    public String getSignName() {
        return signName;
    }

    public void setSignName(String signName) {
        this.signName = signName;
    }

    public static class SmsMessageBuilder {
        private String phone;
        private String bizType;
        private Map<String, String> templateParams;
        private String signName;

        public SmsMessageBuilder phone(String v) {
            this.phone = v;
            return this;
        }

        public SmsMessageBuilder bizType(String v) {
            this.bizType = v;
            return this;
        }

        public SmsMessageBuilder templateParams(Map<String, String> v) {
            this.templateParams = v;
            return this;
        }

        public SmsMessageBuilder signName(String v) {
            this.signName = v;
            return this;
        }

        public SmsMessage build() {
            return new SmsMessage(phone, bizType, templateParams, signName);
        }
    }
}
