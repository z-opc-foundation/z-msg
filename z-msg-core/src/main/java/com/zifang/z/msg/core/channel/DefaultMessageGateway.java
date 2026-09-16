package com.zifang.z.msg.core.channel;

import com.zifang.z.msg.api.*;
import com.zifang.z.msg.core.config.MessageProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息下发路由：根据 z-msg.sms.provider / z-msg.email.provider 配置
 * 从注入的多个 SmsSender / EmailSender Bean 中挑选激活的实现。
 * <p>
 * 长期方案：支持权重路由（多通道并发，主备切换）。
 */
@Component
public class DefaultMessageGateway implements MessageGateway {

    private static final Logger log = LogManager.getLogger(DefaultMessageGateway.class);
    /**
     * name -> sender 缓存（避免每次 list 遍历）
     */
    private final Map<String, SmsSender> smsIndex = new HashMap<>();
    private final Map<String, EmailSender> emailIndex = new HashMap<>();
    @Resource
    private MessageProperties properties;
    @Resource
    private List<SmsSender> smsSenders;
    @Resource
    private List<EmailSender> emailSenders;

    @Override
    public MessageSendResult sendSms(SmsMessage message) {
        SmsSender sender = pickSmsSender();
        if (sender == null) {
            throw new MessageException("MSG_NO_PROVIDER",
                    "没有可用的短信通道 (z-msg.sms.provider=" + properties.getSms().getProvider() + ")");
        }
        log.debug("Routing SMS to provider={}", sender.name());
        return sender.send(message);
    }

    @Override
    public MessageSendResult sendEmail(EmailMessage message) {
        EmailSender sender = pickEmailSender();
        if (sender == null) {
            throw new MessageException("MSG_NO_PROVIDER",
                    "没有可用的邮件通道 (z-msg.email.provider=" + properties.getEmail().getProvider() + ")");
        }
        log.debug("Routing email to provider={}", sender.name());
        return sender.send(message);
    }

    private SmsSender pickSmsSender() {
        if (smsIndex.isEmpty()) {
            for (SmsSender s : smsSenders) smsIndex.put(s.name(), s);
        }
        return smsIndex.get(properties.getSms().getProvider());
    }

    private EmailSender pickEmailSender() {
        if (emailIndex.isEmpty()) {
            for (EmailSender s : emailSenders) emailIndex.put(s.name(), s);
        }
        return emailIndex.get(properties.getEmail().getProvider());
    }
}
