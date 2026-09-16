package com.zifang.z.msg.core.channel;

import com.zifang.util.core.lang.RandomUtil;
import com.zifang.z.msg.api.MessageSendResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * IM 通道工厂 (Phase 3.2)
 * <p>
 * 支持 IM_WECOM (企业微信) / IM_DINGTALK (钉钉) / IM_FEISHU (飞书)。
 * 当前提供 Mock 实现,生产环境替换为各自 SDK 调用。
 */
@Component
public class ImSender {

    private static final Logger log = LogManager.getLogger(ImSender.class);

    public MessageSendResult sendWecom(String userId, String content) {
        return mock("im-wecom", userId, content);
    }

    public MessageSendResult sendDingtalk(String userId, String content) {
        return mock("im-dingtalk", userId, content);
    }

    public MessageSendResult sendFeishu(String userId, String content) {
        return mock("im-feishu", userId, content);
    }

    private MessageSendResult mock(String provider, String userId, String content) {
        String providerMessageId = provider.toUpperCase() + "-" + RandomUtil.uuidShort(16);
        log.info("[{}] userId={} content={} id={}",
                provider.toUpperCase(), userId, content, providerMessageId);
        return MessageSendResult.ok(provider, providerMessageId);
    }

    /**
     * 通用入口:根据 channel 分发
     */
    public MessageSendResult sendByChannel(String channel, String receiver, String content) {
        Map<String, String> renderedContent = new HashMap<>();
        renderedContent.put("content", content);
        switch (channel) {
            case "IM_WECOM":
                return sendWecom(receiver, content);
            case "IM_DINGTALK":
                return sendDingtalk(receiver, content);
            case "IM_FEISHU":
                return sendFeishu(receiver, content);
            default:
                throw new IllegalArgumentException("IM 通道不支持: " + channel);
        }
    }
}
