package com.zifang.z.msg.core.sender;

import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.EmailMessage;
import com.zifang.z.msg.api.EmailSender;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageException;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.api.SmsMessage;
import com.zifang.z.msg.api.SmsSender;
import com.zifang.z.msg.core.config.MessageProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 遗留 SPI 适配层的契约：{@link ChannelSender#send(Message)} 不许抛，
 * 而 1.0.x 的 {@link SmsSender} / {@link EmailSender} 实现（{@code SmtpEmailSender}）
 * 恰恰是用抛 {@link MessageException} 表达"配置不齐 / 运营商拒发"的。
 * <p>
 * 不在这里接住，异常就一路撞到 {@code ChannelRouter} 的兜底 catch，
 * 投递日志里 {@code MSG_SMTP_FROM_MISSING}（"你没配 default-from"）会被记成
 * {@code PROVIDER_EXCEPTION}，和"发信被服务商拒"长一个样，错误码语义丢了，
 * 还可能被重试策略误判成可重试。
 */
class LegacySpiAdapterTest {

    /** 按 1.0.x 的方式抛异常的老邮件实现 */
    static final class ThrowingEmailSender implements EmailSender {
        @Override
        public String name() {
            return "smtp";
        }

        @Override
        public MessageSendResult send(EmailMessage message) {
            throw new MessageException("MSG_SMTP_FROM_MISSING", "z-msg.email.default-from 未配置");
        }
    }

    static final class OkEmailSender implements EmailSender {
        @Override
        public String name() {
            return "smtp";
        }

        @Override
        public MessageSendResult send(EmailMessage message) {
            return MessageSendResult.ok("smtp", "SMTP-1");
        }
    }

    static final class NullReplySmsSender implements SmsSender {
        @Override
        public String name() {
            return "aliyun";
        }

        @Override
        public MessageSendResult send(SmsMessage message) {
            return null;
        }
    }

    static final class RuntimeBoomSmsSender implements SmsSender {
        @Override
        public String name() {
            return "aliyun";
        }

        @Override
        public MessageSendResult send(SmsMessage message) {
            throw new IllegalStateException("socket died");
        }
    }

    private static Message msg(String channel) {
        return Message.builder().channel(channel).receiver("someone").bizType("TEST")
                .subject("s").content("c").build();
    }

    @Test
    void messageExceptionKeepsItsOwnErrorCode() {
        SenderRegistry registry = new SenderRegistry(new MessageProperties(),
                new ArrayList<ChannelSender>(), Collections.<SmsSender>emptyList(),
                Arrays.<EmailSender>asList(new ThrowingEmailSender()));
        ChannelSender picked = registry.pick(Channels.EMAIL, "smtp");
        assertNotNull(picked, "遗留 EmailSender 没被适配进 registry，后面全是空跑");

        MessageSendResult r = picked.send(msg(Channels.EMAIL));

        assertFalse(r.isSuccess());
        assertEquals("MSG_SMTP_FROM_MISSING", r.getErrorCode(),
                "错误码被 router 的兜底 catch 洗成 PROVIDER_EXCEPTION，排查方向就反了");
        assertEquals("smtp", r.getProvider());
    }

    @Test
    void runtimeExceptionBecomesStructuredFailureWithProviderKept() {
        SenderRegistry registry = new SenderRegistry(new MessageProperties(),
                new ArrayList<ChannelSender>(), Arrays.<SmsSender>asList(new RuntimeBoomSmsSender()),
                Collections.<EmailSender>emptyList());
        ChannelSender picked = registry.pick(Channels.SMS, "aliyun");
        assertNotNull(picked, "遗留 SmsSender 没被适配进 registry");

        MessageSendResult r = picked.send(msg(Channels.SMS));

        assertFalse(r.isSuccess());
        assertEquals("PROVIDER_EXCEPTION", r.getErrorCode());
        assertTrue(r.getErrorMessage().contains("socket died"), "异常信息丢了: " + r.getErrorMessage());
        assertEquals("aliyun", r.getProvider());
    }

    @Test
    void nullResultBecomesStructuredFailure() {
        SenderRegistry registry = new SenderRegistry(new MessageProperties(),
                new ArrayList<ChannelSender>(), Arrays.<SmsSender>asList(new NullReplySmsSender()),
                Collections.<EmailSender>emptyList());

        MessageSendResult r = registry.pick(Channels.SMS, "aliyun").send(msg(Channels.SMS));

        assertFalse(r.isSuccess());
        assertEquals("PROVIDER_NULL_RESULT", r.getErrorCode(),
                "老实现返回 null 不能当成“没失败”，否则投递日志记成功、实际什么都没发");
    }

    @Test
    void normalResultPassesThroughUntouched() {
        SenderRegistry registry = new SenderRegistry(new MessageProperties(),
                new ArrayList<ChannelSender>(), Collections.<SmsSender>emptyList(),
                Arrays.<EmailSender>asList(new OkEmailSender()));

        MessageSendResult r = registry.pick(Channels.EMAIL, "smtp").send(msg(Channels.EMAIL));

        // 前三例的正面：适配层不能把所有结果都改写成失败
        assertTrue(r.isSuccess(), "正常返回的老实现被适配层改写了: " + r.getErrorCode());
        assertEquals("SMTP-1", r.getProviderMessageId());
        assertEquals("smtp", r.getProvider());
    }
}
