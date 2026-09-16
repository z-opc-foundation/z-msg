package com.zifang.z.msg.core.channel;

import com.zifang.util.core.lang.RandomUtil;
import com.zifang.z.msg.api.MessageSendResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Push 通道工厂 (Phase 3.3)
 * <p>
 * 支持 PUSH_FCM (Firebase Cloud Messaging - Android) / PUSH_APNS (Apple Push) / PUSH_WEB (Web Push)。
 * 当前提供 Mock 实现,生产环境替换为各自 SDK 调用。
 */
@Component
public class PushSender {

    private static final Logger log = LogManager.getLogger(PushSender.class);

    public MessageSendResult sendFcm(String deviceToken, String title, String body) {
        return mock("push-fcm", deviceToken, title + " | " + body);
    }

    public MessageSendResult sendApns(String deviceToken, String title, String body) {
        return mock("push-apns", deviceToken, title + " | " + body);
    }

    public MessageSendResult sendWeb(String subscription, String title, String body) {
        return mock("push-web", subscription, title + " | " + body);
    }

    public MessageSendResult sendByChannel(String channel, String receiver, String title, String body) {
        switch (channel) {
            case "PUSH_FCM":
                return sendFcm(receiver, title, body);
            case "PUSH_APNS":
                return sendApns(receiver, title, body);
            case "PUSH_WEB":
                return sendWeb(receiver, title, body);
            default:
                throw new IllegalArgumentException("Push 通道不支持: " + channel);
        }
    }

    private MessageSendResult mock(String provider, String receiver, String content) {
        String providerMessageId = provider.toUpperCase() + "-" + RandomUtil.uuidShort(16);
        log.info("[{}] receiver={} content={} id={}",
                provider.toUpperCase(), receiver, content, providerMessageId);
        return MessageSendResult.ok(provider, providerMessageId);
    }
}
