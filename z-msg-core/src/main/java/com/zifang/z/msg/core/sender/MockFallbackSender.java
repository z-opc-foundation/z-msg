package com.zifang.z.msg.core.sender;

import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 没有真实 provider 时的兜底 sender (1.1.0)
 * <p>
 * 1.0.0 的 ImSender / PushSender 直接返回 {@code MessageSendResult.ok(...)} 并编一个
 * providerMessageId，投递日志里看起来"钉钉发成功了"，实际上一个字节都没出去。
 * 这里保留"未接 provider 也能跑通链路"的便利，但用 {@link #isMock()} 明确标成 mock，
 * 由 {@code ChannelRouter} 记 status=3 而不是 status=1，统计与告警都不会被假成功污染。
 */
public class MockFallbackSender implements ChannelSender {

    private static final Logger log = LogManager.getLogger(MockFallbackSender.class);

    /**
     * 只提示一次，避免每个未接入通道都刷日志
     */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private final String channel;

    public MockFallbackSender(String channel) {
        this.channel = Channels.normalize(channel);
    }

    @Override
    public String channel() {
        return channel;
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public boolean isMock() {
        return true;
    }

    @Override
    public MessageSendResult send(Message message) {
        if (WARNED.add(channel)) {
            log.warn("[z-msg] 通道 {} 尚无真实 provider，走 mock 发送（投递日志记 status=3 非成功）。"
                    + "接入方式：实现 ChannelSender 并配 z-msg.channel.{}.provider",
                    channel, channel == null ? "" : channel.toLowerCase());
        }
        String id = "MOCK-" + channel + "-" + Long.toHexString(System.nanoTime());
        if (log.isDebugEnabled()) {
            log.debug("[z-msg][mock] {} receiver={} msgId={}", channel,
                    message == null ? null : message.getReceiver(), id);
        }
        return MessageSendResult.ok("mock", id);
    }
}
