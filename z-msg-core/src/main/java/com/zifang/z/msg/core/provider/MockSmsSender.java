package com.zifang.z.msg.core.provider;

import com.zifang.util.core.lang.RandomUtil;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.api.SmsMessage;
import com.zifang.z.msg.api.SmsSender;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Mock 短信实现：仅打印日志 + 返回伪造 messageId
 * <p>
 * 用于开发、测试、E2E 冒烟，不依赖任何第三方 SMS 通道。
 * 长期方案：生产环境切到 AliyunSmsSender / TencentSmsSender，只需在 yml 改 provider 名字。
 */
@Component
public class MockSmsSender implements SmsSender {

    private static final Logger log = LogManager.getLogger(MockSmsSender.class);

    @Resource
    private MessageTemplateEngine templateEngine;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public MessageSendResult send(SmsMessage message) {
        String text = templateEngine.render(message.getBizType(), message.getTemplateParams());
        String providerMessageId = "MOCK-SMS-" + RandomUtil.uuidShort(16);
        log.info("[MOCK-SMS] -> phone={} bizType={} text={} providerMessageId={}",
                message.getPhone(), message.getBizType(), text, providerMessageId);
        return MessageSendResult.ok(name(), providerMessageId);
    }
}
