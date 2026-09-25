package com.zifang.z.msg.core.sender;

import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.core.config.MessageProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * provider 默认选择的回归面。
 * <p>
 * 钉的是这条真实事故：{@code MessageProperties.Channel.provider} 的字段初值曾经是
 * {@code "mock"}，于是 {@code providerOf("IN_APP")} 永远返回 mock、站内信全部落到
 * {@code MockFallbackSender} 上回 success，{@code z_msg_message} 一行不写，收件箱恒空。
 * 这个 bug 在 HTTP 层只表现为"列表是空的"，看不出原因，所以在注册表这一层直接钉住。
 */
public class SenderRegistryProviderDefaultTest {

    private static ChannelSender stub(final String channel, final String provider, final boolean mock) {
        return new ChannelSender() {
            @Override
            public String channel() {
                return channel;
            }

            @Override
            public String provider() {
                return provider;
            }

            @Override
            public MessageSendResult send(Message message) {
                return MessageSendResult.ok(provider, "STUB-" + message.getBizType());
            }

            @Override
            public boolean isMock() {
                return mock;
            }
        };
    }

    /** core 自带的实现都注册进来，等价于宿主只引了 z-msg-core、一行 yml 没写。 */
    private static SenderRegistry bareRegistry(MessageProperties props) {
        return new SenderRegistry(props, Arrays.asList(
                stub(Channels.IN_APP, "db", false),
                stub(Channels.SMS, "mock", true),
                stub(Channels.EMAIL, "mock", true)),
                Collections.<com.zifang.z.msg.api.SmsSender>emptyList(),
                Collections.<com.zifang.z.msg.api.EmailSender>emptyList());
    }

    @Test
    public void inAppPicksTheBuiltinImplementationWithoutAnyConfiguration() {
        MessageProperties props = new MessageProperties();
        SenderRegistry registry = bareRegistry(props);

        assertEquals("db", props.providerOf(Channels.IN_APP),
                "站内信有 core 自带的 db 实现，默认就不该是 mock");
        assertFalse(registry.pick(Channels.IN_APP).isMock(),
                "pick(IN_APP) 必须给真实现；给 mock 的话消息会假成功且不落库");
        assertEquals("db", registry.pick(Channels.IN_APP).provider());
    }

    @Test
    public void channelsWithoutABuiltinImplementationStillFallBackToMock() {
        MessageProperties props = new MessageProperties();
        SenderRegistry registry = bareRegistry(props);

        // 反向对照：上一条如果是因为"所有通道都不再兜底 mock"而变绿，这里就会红。
        // 没有厂商凭据时 SMS 必须还是 mock，而且要能被认出来是 mock。
        assertTrue(registry.pick(Channels.SMS).isMock(), "SMS 没配厂商时应落在 mock 上");
        assertFalse(registry.hasRealSender(Channels.SMS));
        assertTrue(registry.hasRealSender(Channels.IN_APP), "IN_APP 有 db 实现，应报\"有真通道\"");
    }

    @Test
    public void explicitProviderOverridesTheBuiltinDefault() {
        MessageProperties props = new MessageProperties();
        MessageProperties.Channel cfg = new MessageProperties.Channel();
        cfg.setProvider("mock");
        props.getChannel().put(Channels.IN_APP, cfg);

        // 显式要 mock 仍然有效——"默认改成 db"不能变成"不给你关"
        assertEquals("mock", props.providerOf(Channels.IN_APP));
        assertTrue(bareRegistry(props).pick(Channels.IN_APP).isMock());
    }

    @Test
    public void providerLookupIsCaseAndSeparatorInsensitive() {
        MessageProperties props = new MessageProperties();
        MessageProperties.Channel cfg = new MessageProperties.Channel();
        cfg.setProvider("smtp");
        props.getChannel().put("EMAIL", cfg);

        assertEquals("smtp", props.providerOf("email"), "小写通道名要能命中大写 key");
        assertEquals("smtp", props.providerOf("Email"));
    }
}
