package com.zifang.z.msg.core.sender;

import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.EmailMessage;
import com.zifang.z.msg.api.EmailSender;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.api.SmsMessage;
import com.zifang.z.msg.api.SmsSender;
import com.zifang.z.msg.core.config.MessageProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider 注册表 (1.1.0)
 * <p>
 * 把"通道 + 配置"解析成具体 {@link ChannelSender}，替代 1.0.0 里
 * {@code DefaultMessageGateway} 那两个非线程安全、永不失效的 lazy HashMap。
 * <ul>
 *   <li>收集 Spring 容器内全部 ChannelSender Bean（z-msg-channels 里的真实 provider 由此接入）</li>
 *   <li>兼容 1.0.0 的 SmsSender / EmailSender SPI：自动包一层 adapter，老代码不用改</li>
 *   <li>provider 查找走 {@link MessageProperties#providerOf(String)}，未注册时回落 mock 并告警</li>
 * </ul>
 */
public class SenderRegistry {

    private static final Logger log = LogManager.getLogger(SenderRegistry.class);

    private final MessageProperties properties;
    /**
     * "CHANNEL|provider" -> sender，key 全大写
     */
    private final Map<String, ChannelSender> index = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> providersByChannel = new ConcurrentHashMap<>();
    private final Map<String, ChannelSender> mockFallbacks = new ConcurrentHashMap<>();

    public SenderRegistry(MessageProperties properties,
                          List<ChannelSender> channelSenders,
                          List<SmsSender> legacySmsSenders,
                          List<EmailSender> legacyEmailSenders) {
        this.properties = properties;
        if (channelSenders != null) {
            for (ChannelSender s : channelSenders) {
                register(s);
            }
        }
        if (legacySmsSenders != null) {
            for (SmsSender s : legacySmsSenders) {
                register(new LegacySmsAdapter(s));
            }
        }
        if (legacyEmailSenders != null) {
            for (EmailSender s : legacyEmailSenders) {
                register(new LegacyEmailAdapter(s));
            }
        }
        log.info("[z-msg] SenderRegistry 就绪：{} 个通道，{} 个 provider",
                providersByChannel.size(), index.size());
    }

    public final void register(ChannelSender sender) {
        if (sender == null || sender.channel() == null) {
            return;
        }
        String channel = Channels.normalize(sender.channel());
        String provider = sender.provider() == null ? "default" : sender.provider().trim().toLowerCase();
        index.put(key(channel, provider), sender);
        providersByChannel.computeIfAbsent(channel, k -> new LinkedHashSet<>()).add(provider);
    }

    /**
     * 按配置取该通道激活的 sender；未注册则给 mock 兜底（永不返回 null）。
     */
    public ChannelSender pick(String channel) {
        String ch = Channels.normalize(channel);
        if (ch == null) {
            throw new IllegalArgumentException("channel 不能为空");
        }
        String provider = normalizeProvider(properties.providerOf(ch));
        ChannelSender s = index.get(key(ch, provider));
        if (s != null) {
            return s;
        }
        return mockFallback(ch);
    }

    /**
     * 指定 provider 取，取不到返回 null（用于 fan-out 降级链）
     */
    public ChannelSender pick(String channel, String provider) {
        if (channel == null || provider == null) {
            return null;
        }
        return index.get(key(Channels.normalize(channel), normalizeProvider(provider)));
    }

    public boolean hasRealSender(String channel) {
        String ch = Channels.normalize(channel);
        Set<String> providers = providersByChannel.get(ch);
        if (providers == null || providers.isEmpty()) {
            return false;
        }
        for (String p : providers) {
            ChannelSender s = index.get(key(ch, p));
            if (s != null && !s.isMock()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 该通道可用的 provider 名列表（{@code GET /api/msg/channel/list} 自省用）
     */
    public List<String> providers(String channel) {
        Set<String> p = providersByChannel.get(Channels.normalize(channel));
        return p == null ? Collections.emptyList() : new ArrayList<>(p);
    }

    public Set<String> channels() {
        return Collections.unmodifiableSet(providersByChannel.keySet());
    }

    public String activeProvider(String channel) {
        return normalizeProvider(properties.providerOf(channel));
    }

    private ChannelSender mockFallback(String channel) {
        return mockFallbacks.computeIfAbsent(channel, MockFallbackSender::new);
    }

    private static String normalizeProvider(String provider) {
        return provider == null ? "default" : provider.trim().toLowerCase();
    }

    private static String key(String channel, String provider) {
        return channel + "|" + provider;
    }

    /**
     * 遗留 SmsSender → ChannelSender
     */
    static class LegacySmsAdapter implements ChannelSender {
        private final SmsSender delegate;

        LegacySmsAdapter(SmsSender delegate) {
            this.delegate = delegate;
        }

        @Override
        public String channel() {
            return Channels.SMS;
        }

        @Override
        public String provider() {
            return delegate.name();
        }

        @Override
        public boolean isMock() {
            return isMockName(delegate.name(), delegate.getClass().getSimpleName());
        }

        @Override
        public MessageSendResult send(Message message) {
            SmsMessage sms = SmsMessage.builder()
                    .phone(message.getReceiver())
                    .bizType(message.getBizType())
                    .templateParams(message.getParams())
                    .signName(message.param("signName", null))
                    .build();
            return delegate.send(sms);
        }
    }

    /**
     * 遗留 EmailSender → ChannelSender
     */
    static class LegacyEmailAdapter implements ChannelSender {
        private final EmailSender delegate;

        LegacyEmailAdapter(EmailSender delegate) {
            this.delegate = delegate;
        }

        @Override
        public String channel() {
            return Channels.EMAIL;
        }

        @Override
        public String provider() {
            return delegate.name();
        }

        @Override
        public boolean isMock() {
            return isMockName(delegate.name(), delegate.getClass().getSimpleName());
        }

        @Override
        public MessageSendResult send(Message message) {
            EmailMessage email = EmailMessage.builder()
                    .to(message.getReceiver())
                    .bizType(message.getBizType())
                    .templateParams(message.getParams())
                    .subject(message.getSubject())
                    .build();
            return delegate.send(email);
        }
    }

    private static boolean isMockName(String provider, String simpleClassName) {
        return "mock".equalsIgnoreCase(provider)
                || (simpleClassName != null && simpleClassName.toLowerCase().contains("mock"));
    }
}
