package com.zifang.z.msg.channels.provider;

import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 录制 mock provider：不外发，把每条消息记进内存供断言/本地自测。
 * <p>
 * 两个必须的行为（{@link ChannelSender} 契约）：
 * <ul>
 *   <li>{@link #isMock()} 恒 true —— 投递日志记 status=3 而不是成功，防假成功流入生产统计；</li>
 *   <li>一个类服务多个通道（channel 由构造参数定），provider 名固定 {@code mock}，
 *       与 {@code SenderRegistry} 的 mock 兜底同名但语义更强：这里能拿到请求内容。</li>
 * </ul>
 * 自动装配默认为四个 IM 机器人通道各注册一个（宿主显式配 provider=robot 时用真实 provider）。
 * 宿主也可以不依赖 Spring，直接 {@code new RecordingMockSender(Channels.PUSH_FCM)} 注册自己的通道。
 */
public class RecordingMockSender implements ChannelSender {

    /**
     * 一条被录制的发送请求
     */
    public static final class Recorded {
        public final String channel;
        public final String receiver;
        public final String bizType;
        public final String subject;
        public final String content;
        public final String msgId;

        Recorded(String channel, String receiver, String bizType, String subject, String content, String msgId) {
            this.channel = channel;
            this.receiver = receiver;
            this.bizType = bizType;
            this.subject = subject;
            this.content = content;
            this.msgId = msgId;
        }

        @Override
        public String toString() {
            return "Recorded{" + channel + "," + receiver + "," + bizType + "," + msgId + "}";
        }
    }

    private final String channel;
    private final List<Recorded> recorded = Collections.synchronizedList(new ArrayList<Recorded>());

    public RecordingMockSender(String channel) {
        this.channel = channel;
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
        if (message == null) {
            return MessageSendResult.fail("mock", "INVALID_MESSAGE", "message 为 null");
        }
        recorded.add(new Recorded(channel, message.getReceiver(), message.getBizType(),
                message.getSubject(), message.getContent(), message.getMsgId()));
        return MessageSendResult.ok("mock", "MOCK-" + message.getMsgId());
    }

    public List<Recorded> recorded() {
        synchronized (recorded) {
            return new ArrayList<>(recorded);
        }
    }

    public void clear() {
        recorded.clear();
    }
}
