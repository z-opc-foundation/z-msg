package com.zifang.z.msg.core.provider;

import com.zifang.util.core.lang.RandomUtil;
import com.zifang.z.msg.api.EmailMessage;
import com.zifang.z.msg.api.EmailSender;
import com.zifang.z.msg.api.MessageException;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.core.config.MessageProperties;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;

/**
 * 真实 SMTP 邮件发送 (FEATURE011 阶段 - 邮箱注册用)。
 * <p>
 * Provider 名 {@code smtp}，配置项：
 * <pre>
 * z-msg:
 *   email:
 *     provider: smtp
 *     smtp-host: smtp.qq.com          # QQ 邮箱
 *     smtp-port: 465
 *     smtp-username: xxx@qq.com
 *     smtp-password: xxxxxxxx         # 授权码 (环境变量 MAIL_SMTP_PASSWORD 注入)
 *     default-from: xxx@qq.com        # 与 username 一致 (QQ 强制要求)
 * </pre>
 * <p>
 * 走 spring-boot-starter-mail 的 {@link JavaMailSender}，SSL 465 时自动启 {@code mail.smtp.ssl.enable=true}。
 * 模板与 MockEmailSender 共享 {@link MessageTemplateEngine}。
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LogManager.getLogger(SmtpEmailSender.class);

    @Resource
    private MessageTemplateEngine templateEngine;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private MessageProperties properties;

    @Override
    public String name() {
        return "smtp";
    }

    @Override
    public MessageSendResult send(EmailMessage message) {
        // SMTP 走 JavaMailSender (由 spring-boot-starter-mail + spring.mail.* 自动装配)
        // 这里只读 z-msg.email.* 的元数据 (provider 路由 + defaultFrom)
        if (properties.getEmail().getDefaultFrom() == null
                || properties.getEmail().getDefaultFrom().isEmpty()) {
            throw new MessageException("MSG_SMTP_FROM_MISSING",
                    "z-msg.email.default-from 未配置（即发件人邮箱），邮件发送终止。");
        }

        String body = templateEngine.renderEmailBody(message.getBizType(), message.getTemplateParams());
        String subject = message.getSubject() != null && !message.getSubject().isEmpty()
                ? message.getSubject()
                : templateEngine.renderEmailSubject(message.getBizType());
        String from = properties.getEmail().getDefaultFrom();

        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(message.getTo());
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(mime);

            String providerMessageId = "SMTP-" + RandomUtil.uuidShort(16);
            log.info("[SMTP-EMAIL] to={} from={} subject={} providerMessageId={} bizType={}",
                    message.getTo(), from, subject, providerMessageId, message.getBizType());
            return MessageSendResult.ok(name(), providerMessageId);
        } catch (MessagingException e) {
            log.error("[SMTP-EMAIL-FAILED] to={} from={} subject={} err={}",
                    message.getTo(), from, subject, e.getMessage(), e);
            throw new MessageException("MSG_SMTP_SEND_FAILED",
                    "SMTP 发送失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[SMTP-EMAIL-ERROR] to={} from={} err={}", message.getTo(), from, e.getMessage(), e);
            throw new MessageException("MSG_SMTP_ERROR",
                    "SMTP 异常: " + e.getMessage());
        }
    }
}
