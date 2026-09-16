package com.zifang.z.msg.core.provider;

import com.zifang.util.core.lang.RandomUtil;
import com.zifang.z.msg.api.EmailMessage;
import com.zifang.z.msg.api.EmailSender;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Mock 邮件实现：仅打印日志 + 返回伪造 messageId
 */
@Component
public class MockEmailSender implements EmailSender {

    private static final Logger log = LogManager.getLogger(MockEmailSender.class);

    @Resource
    private MessageTemplateEngine templateEngine;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public MessageSendResult send(EmailMessage message) {
        String body = templateEngine.renderEmailBody(message.getBizType(), message.getTemplateParams());
        String subject = message.getSubject() != null && !message.getSubject().isEmpty()
                ? message.getSubject()
                : templateEngine.renderEmailSubject(message.getBizType());
        String providerMessageId = "MOCK-EMAIL-" + RandomUtil.uuidShort(16);
        log.info("[MOCK-EMAIL] -> to={} bizType={} subject={} body={} providerMessageId={}",
                message.getTo(), message.getBizType(), subject, body, providerMessageId);
        return MessageSendResult.ok(name(), providerMessageId);
    }
}
