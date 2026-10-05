package com.zifang.z.msg.core.channel;

import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.core.config.MessageProperties;
import com.zifang.z.msg.core.domain.mapper.MsgDeliveryLogMapper;
import com.zifang.z.msg.core.domain.service.MsgUserPreferenceService;
import com.zifang.z.msg.core.ratelimit.ChannelRateLimiter;
import com.zifang.z.msg.core.sender.SenderRegistry;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出站幂等表 {@code ChannelRouter.issued} 的落位时机。
 *
 * <p>{@code Message.idempotencyKey} 的契约是「同 channel + bizType + idempotencyKey 只投递一次」。
 * 这里钉的是"占位"与"实际是否送达"是否对齐：</p>
 *
 * <ol>
 *   <li>主通道直接成功 → 必须占位（对照组：证明幂等表本身是通的，不是在测一个从没接线的功能）；</li>
 *   <li>主通道失败、降级通道成功 → 消息**已经送到用户手里了**，同样必须占位；</li>
 *   <li>主通道失败且没有降级成功 → 消息没送出去，绝不能占位，否则一次网络抖动就把这条消息
 *       永久钉死在 24 小时幂等窗口里，运维重放也发不出去。</li>
 * </ol>
 */
class ChannelRouterIdempotencyTest {

    /** 记录自己被调了几次，便于断言"第二次到底有没有真发出去"。 */
    private static final class CountingSender implements ChannelSender {
        private final String channel;
        private final String provider;
        private final boolean succeed;
        int calls;

        CountingSender(String channel, String provider, boolean succeed) {
            this.channel = channel;
            this.provider = provider;
            this.succeed = succeed;
        }

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
            calls++;
            return succeed
                    ? MessageSendResult.ok(provider, "OK-" + calls)
                    : MessageSendResult.fail(provider, "PROVIDER_DOWN", "模拟厂商故障");
        }

        @Override
        public boolean ready() {
            return true;
        }
    }

    private MessageProperties props;

    @BeforeEach
    void setUp() {
        props = new MessageProperties();
        // 关掉重试与限流，钉"provider 被调了几次"时不掺退避 sleep 和配额
        props.getRetry().setMaxAttempts(0);
        props.getRateLimit().setEnabled(false);
    }

    private void channelCfg(String channelName, String provider, String... fallbacks) {
        MessageProperties.Channel ch = new MessageProperties.Channel();
        ch.setProvider(provider);
        ch.setFallbackChannels(new ArrayList<String>(Arrays.asList(fallbacks)));
        props.getChannel().put(channelName, ch);
    }

    private ChannelRouter newRouter(ChannelSender... senders) {
        SenderRegistry registry = new SenderRegistry(props,
                new ArrayList<ChannelSender>(Arrays.asList(senders)), new ArrayList<>(), new ArrayList<>());
        ChannelRouter router = new ChannelRouter();
        ReflectionTestUtils.setField(router, "senderRegistry", registry);
        ReflectionTestUtils.setField(router, "properties", props);
        ReflectionTestUtils.setField(router, "rateLimiter", new ChannelRateLimiter(1000));
        // subject/content 都填了，renderInPlace 走不到引擎，templateEngine 不会被碰
        ReflectionTestUtils.setField(router, "templateEngine", Mockito.mock(MessageTemplateEngine.class));
        ReflectionTestUtils.setField(router, "preferenceService", Mockito.mock(MsgUserPreferenceService.class));
        ReflectionTestUtils.setField(router, "deliveryLogMapper", Mockito.mock(MsgDeliveryLogMapper.class));
        return router;
    }

    private static Message msg(String channel, String idemKey) {
        Map<String, String> params = new HashMap<>();
        params.put("receiver:push_apns", "device-1");
        return Message.builder()
                .channel(channel)
                .bizType("ORDER_PAID")
                .subject("支付成功")
                .content("您的订单已支付")
                .idempotencyKey(idemKey)
                .params(params)
                .build();
    }

    /** 对照组：主通道直接成功时，幂等表必须是通的。 */
    @Test
    void 主通道成功时第二次被幂等拦下() {
        CountingSender primary = new CountingSender(Channels.PUSH_FCM, "ok-provider", true);
        channelCfg("push_fcm", "ok-provider");
        ChannelRouter router = newRouter(primary);

        assertTrue(router.route(msg(Channels.PUSH_FCM, "order-1")).isSuccess());
        MessageSendResult second = router.route(msg(Channels.PUSH_FCM, "order-1"));

        assertEquals(1, primary.calls, "同一幂等键第二次不该真发出去");
        assertEquals("DUPLICATED", second.getErrorCode(),
                "第二次应当判为幂等命中（前提：幂等表本身是通的）");
    }

    /** 主测：消息经降级真的送到了用户手里，也必须占位。 */
    @Test
    void 降级送达后第二次仍被幂等拦下() {
        CountingSender primary = new CountingSender(Channels.PUSH_FCM, "down", false);
        CountingSender backup = new CountingSender(Channels.PUSH_APNS, "ok-provider", true);
        channelCfg("push_fcm", "down", Channels.PUSH_APNS);
        channelCfg("push_apns", "ok-provider");
        ChannelRouter router = newRouter(primary, backup);

        assertTrue(router.route(msg(Channels.PUSH_FCM, "order-1")).isSuccess(),
                "前提：降级链路本身是通的");

        router.route(msg(Channels.PUSH_FCM, "order-1"));

        assertEquals(1, backup.calls,
                "消息已由降级通道送达，同一幂等键的第二次请求不该让用户再收一条");
    }

    /** 反向：没送出去就不许占位，否则一次抖动就把消息永久钉死。 */
    @Test
    void 投递失败不占幂等位以便重放() {
        CountingSender primary = new CountingSender(Channels.PUSH_FCM, "down", false);
        channelCfg("push_fcm", "down");
        ChannelRouter router = newRouter(primary);

        assertTrue(!router.route(msg(Channels.PUSH_FCM, "order-1")).isSuccess(), "前提：主通道失败");

        router.route(msg(Channels.PUSH_FCM, "order-1"));

        assertEquals(2, primary.calls,
                "消息没送出去，不该被幂等表钉死——重放必须能真发出去");
    }
}
