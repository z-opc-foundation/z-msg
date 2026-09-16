package com.zifang.z.msg.api;

/**
 * 邮件下发通道 SPI
 * <p>
 * 实现方可以是 Mock（开发环境）、SMTP、企业邮箱 API、SendGrid 等。
 */
public interface EmailSender {

    /**
     * 通道名（如 "mock" / "smtp" / "sendgrid"），与 application.yml 中 z-msg.email.provider 对应
     */
    String name();

    /**
     * 发送邮件
     *
     * @param message 邮件内容
     * @return 发送结果
     */
    MessageSendResult send(EmailMessage message);
}
