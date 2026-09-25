package com.zifang.z.msg.core.channel;

import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.EmailMessage;
import com.zifang.z.msg.api.FanOutResult;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageGateway;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.api.SmsMessage;
import com.zifang.z.msg.core.sender.SenderRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 消息下发路由：所有通道统一走 {@link ChannelRouter} 这条管道。
 * <p>
 * 1.0.0 的实现只认 sms/email 两个遗留接口，且用两个永不失效的非线程安全
 * HashMap 做 provider 缓存；provider 改名/加新通道（钉钉、企微、SendGrid）时
 * 这个类必须跟着改，等于"通道无关"是假的。1.1.0 起它只做参数整形 + 委派，
 * 具体 provider 查找在 {@link SenderRegistry}。
 */
@Component
public class DefaultMessageGateway implements MessageGateway {

    private static final Logger log = LogManager.getLogger(DefaultMessageGateway.class);

    @Resource
    private ChannelRouter channelRouter;
    @Resource
    private SenderRegistry senderRegistry;

    @Override
    public MessageSendResult sendSms(SmsMessage message) {
        return send(Message.builder()
                .channel(Channels.SMS)
                .receiver(message.getPhone())
                .bizType(message.getBizType())
                .params(message.getTemplateParams())
                .build());
    }

    @Override
    public MessageSendResult sendEmail(EmailMessage message) {
        return send(Message.builder()
                .channel(Channels.EMAIL)
                .receiver(message.getTo())
                .bizType(message.getBizType())
                .params(message.getTemplateParams())
                .subject(message.getSubject())
                .build());
    }

    @Override
    public MessageSendResult send(Message message) {
        if (message == null || message.getChannel() == null) {
            throw new IllegalArgumentException("Message.channel 不能为空");
        }
        ChannelSender sender = senderRegistry.pick(message.getChannel());
        if (log.isDebugEnabled()) {
            log.debug("[Gateway] channel={} provider={} mock={}",
                    Channels.normalize(message.getChannel()), sender.provider(), sender.isMock());
        }
        return channelRouter.route(message);
    }

    @Override
    public FanOutResult fanOut(String bizType, Long userId, Map<String, String> receivers,
                               Map<String, String> params, String locale) {
        return channelRouter.fanOut(bizType, userId, receivers, params, locale);
    }

    /**
     * 以一条消息为模板向多通道 fan-out
     */
    public FanOutResult fanOut(Message base, Map<String, String> receivers) {
        return channelRouter.fanOut(base, receivers);
    }
}
