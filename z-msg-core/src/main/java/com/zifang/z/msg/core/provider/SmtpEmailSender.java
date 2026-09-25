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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

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
 * 走 spring-boot-starter-mail 的 {@link JavaMailSender}：宿主配了 {@code spring.mail.host} 就直接用
 * Boot 装配好的那一个；只配了 {@code z-msg.email.smtp-*} 时由本类自己按这些值建会话
 * （1.0.x 里 {@code smtp-host/port/username/password} 四项是死配置，没人读，README 却照样写着）。
 * <p>
 * 关键约束：本 bean 的存在不能依赖 SMTP 是否可用。它是 {@code @ComponentScan} 扫进来的，
 * 一旦硬依赖 {@link JavaMailSender}，没配邮件的宿主连上下文都起不来——
 * 一个"短信/站内信也能用"的消息库不该要求先摆一台 SMTP 服务器。
 * 真正的凭据缺失在 {@link #send} 时以 {@code MSG_SMTP_NOT_CONFIGURED} 明确报出。
 * <p>
 * 模板与 MockEmailSender 共享 {@link MessageTemplateEngine}。
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LogManager.getLogger(SmtpEmailSender.class);

    /** 隐式 SSL 端口（465）与提交端口（587）之外的端口不猜加密方式。 */
    private static final int IMPLICIT_SSL_PORT = 465;
    private static final int STARTTLS_PORT = 587;

    @Resource
    private MessageTemplateEngine templateEngine;

    @Resource
    private MessageProperties properties;

    /** Boot 的 {@code MailSenderAutoConfiguration} 只在有 {@code spring.mail.host} 时才建 bean，所以必须按可选处理。 */
    @Resource
    private ObjectProvider<JavaMailSender> bootMailSenders;

    /** 首次发送时解析并缓存；null 表示还没解析过。 */
    private volatile JavaMailSender resolvedSender;

    @Override
    public String name() {
        return "smtp";
    }

    @Override
    public MessageSendResult send(EmailMessage message) {
        // 凭据解析放在 try 之外：MessageException 若落在 try 里会被下面的兜底 catch
        // 重新包成 MSG_SMTP_ERROR，"你没配" 就变成 "未知异常"，排查方向直接反了。
        if (properties.getEmail().getDefaultFrom() == null
                || properties.getEmail().getDefaultFrom().isEmpty()) {
            throw new MessageException("MSG_SMTP_FROM_MISSING",
                    "z-msg.email.default-from 未配置（即发件人邮箱），邮件发送终止。");
        }
        JavaMailSender mailSender = resolveSender();

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

    /**
     * 解析出可用的 {@link JavaMailSender} 并缓存。
     * <p>
     * 优先级：{@code spring.mail.*}（Boot 装配的那一个，行为与宿主其它邮件用法一致）
     * &gt; {@code z-msg.email.smtp-*}（本类自建）。都没配就抛——不是回到 mock，
     * 因为 {@code provider=smtp} 是宿主显式声明的意图，悄悄退回假成功比报错更糟。
     */
    private JavaMailSender resolveSender() {
        JavaMailSender cached = resolvedSender;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (resolvedSender != null) {
                return resolvedSender;
            }
            JavaMailSender fromBoot = bootMailSenders.getIfAvailable();
            if (fromBoot != null) {
                resolvedSender = fromBoot;
                return fromBoot;
            }
            MessageProperties.Email email = properties.getEmail();
            String host = email.getSmtpHost();
            if (host == null || host.trim().isEmpty()) {
                throw new MessageException("MSG_SMTP_NOT_CONFIGURED",
                        "z-msg.email.provider=smtp，但 spring.mail.host 和 z-msg.email.smtp-host 都没配；"
                                + "站任意一处配置即可（配了 spring.mail.* 时 z-msg.email.smtp-* 不再生效）。");
            }
            // 端口不猜 25：25 上不套 TLS 又带 auth，等于把口令明文发出去。
            // 缺省按 465 隐式 SSL——javadoc 里给的示例就是它，也是国内邮箱的主流端口。
            int port = email.getSmtpPort() == null ? IMPLICIT_SSL_PORT : email.getSmtpPort();
            JavaMailSenderImpl impl = new JavaMailSenderImpl();
            impl.setHost(host.trim());
            impl.setPort(port);
            impl.setUsername(email.getSmtpUsername());
            impl.setPassword(email.getSmtpPassword());
            impl.setDefaultEncoding(StandardCharsets.UTF_8.name());
            Properties props = impl.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", email.getSmtpUsername() == null ? "false" : "true");
            if (port == IMPLICIT_SSL_PORT) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.socketFactory.port", String.valueOf(port));
            } else if (port == STARTTLS_PORT) {
                props.put("mail.smtp.starttls.enable", "true");
            }
            log.info("[SMTP-EMAIL] z-msg.email.smtp-* 自建会话 host={} port={} auth={}",
                    impl.getHost(), port, props.get("mail.smtp.auth"));
            resolvedSender = impl;
            return impl;
        }
    }
}
