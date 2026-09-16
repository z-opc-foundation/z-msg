package com.zifang.z.msg.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * z-msg 配置属性
 * <p>
 * application.yml 示例：
 * <pre>
 * z-msg:
 *   sms:
 *     provider: mock
 *     default-sign: 【z-opc】
 *   email:
 *     provider: mock
 *     default-from: no-reply@z-opc.com
 *   template:
 *     # bizType -> templateId（短信模板或邮件模板 ID）
 *     REGISTER: TEMPLATE_REGISTER
 *     LOGIN: TEMPLATE_LOGIN
 *     RESET_PWD: TEMPLATE_RESET_PWD
 * </pre>
 */
@ConfigurationProperties(prefix = "z-msg")
public class MessageProperties {

    private Sms sms = new Sms();
    private Email email = new Email();
    private java.util.Map<String, String> template = new java.util.HashMap<>();

    public Sms getSms() {
        return sms;
    }

    public void setSms(Sms sms) {
        this.sms = sms;
    }

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }

    public java.util.Map<String, String> getTemplate() {
        return template;
    }

    public void setTemplate(java.util.Map<String, String> template) {
        this.template = template;
    }

    public static class Sms {
        /**
         * 当前激活的 provider 名称（"mock" / "aliyun" / "tencent"）
         */
        private String provider = "mock";
        private String defaultSign = "【z-opc】";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getDefaultSign() {
            return defaultSign;
        }

        public void setDefaultSign(String defaultSign) {
            this.defaultSign = defaultSign;
        }
    }

    public static class Email {
        private String provider = "mock";
        private String defaultFrom = "no-reply@z-opc.com";
        /**
         * SMTP 配置（生产用）
         */
        private String smtpHost;
        private Integer smtpPort;
        private String smtpUsername;
        private String smtpPassword;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getDefaultFrom() {
            return defaultFrom;
        }

        public void setDefaultFrom(String defaultFrom) {
            this.defaultFrom = defaultFrom;
        }

        public String getSmtpHost() {
            return smtpHost;
        }

        public void setSmtpHost(String smtpHost) {
            this.smtpHost = smtpHost;
        }

        public Integer getSmtpPort() {
            return smtpPort;
        }

        public void setSmtpPort(Integer smtpPort) {
            this.smtpPort = smtpPort;
        }

        public String getSmtpUsername() {
            return smtpUsername;
        }

        public void setSmtpUsername(String smtpUsername) {
            this.smtpUsername = smtpUsername;
        }

        public String getSmtpPassword() {
            return smtpPassword;
        }

        public void setSmtpPassword(String smtpPassword) {
            this.smtpPassword = smtpPassword;
        }
    }
}
