package com.zifang.z.msg.channels;

import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.channels.config.ChannelsProperties;
import com.zifang.z.msg.channels.http.SimpleHttpClient;
import com.zifang.z.msg.channels.provider.DingTalkRobotSender;
import com.zifang.z.msg.channels.support.StubServer;
import com.zifang.z.msg.core.channel.ChannelRouter;
import com.zifang.z.msg.core.config.MessageProperties;
import com.zifang.z.msg.core.domain.entity.MsgDeliveryLogDO;
import com.zifang.z.msg.core.domain.mapper.MsgDeliveryLogMapper;
import com.zifang.z.msg.core.domain.service.MsgUserPreferenceService;
import com.zifang.z.msg.core.ratelimit.ChannelRateLimiter;
import com.zifang.z.msg.core.sender.SenderRegistry;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不碰 router/gateway 源码，验证"新增渠道 = 一个 ChannelSender bean + 一段 yml"这句话：
 * <ol>
 *   <li>provider bean + MessageProperties 里一个 provider 名，{@code SenderRegistry.pick}
 *       就命中它（这就是 core 的接线方式，MsgCoreConfiguration 用 ObjectProvider 收集）；</li>
 *   <li>配置不齐时 {@code ChannelRouter} 实测行为 = 跳过并记 PROVIDER_NOT_CONFIGURED
 *       （不打 HTTP、不抛异常）；配上之后同一个 router 实例真的把消息投出去（正例对照）。</li>
 * </ol>
 * ChannelRouter 的私有 @Resource 字段用 ReflectionTestUtils 注入（等价于 Spring 的接线，
 * 但不需要拉整个上下文/数据库）。
 */
class RouterWiringTest {

    private StubServer stub;
    private ChannelsProperties channelsProps;
    private MessageProperties messageProperties;
    private SenderRegistry registry;
    private ChannelRouter router;
    private DingTalkRobotSender dingTalk;
    private MsgDeliveryLogMapper deliveryLog;

    /** 一个"新渠道"演示实现：core/web/ws 都不知道它的存在。 */
    static final class DemoPushSender implements ChannelSender {
        final List<Message> got = new ArrayList<>();

        @Override
        public String channel() {
            return Channels.PUSH_FCM;
        }

        @Override
        public String provider() {
            return "demo";
        }

        @Override
        public MessageSendResult send(Message message) {
            got.add(message);
            return MessageSendResult.ok("demo", "DEMO-" + message.getMsgId());
        }

        @Override
        public boolean ready() {
            return true;
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubServer();
        channelsProps = new ChannelsProperties();
        messageProperties = new MessageProperties();
        // 关掉重试，钉"provider 被调用次数"时不掺 HTTP 层以外的量
        messageProperties.getRetry().setMaxAttempts(0);
        messageProperties.getRateLimit().setEnabled(false);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private void wire(List<ChannelSender> senders) {
        registry = new SenderRegistry(messageProperties, senders,
                new ArrayList<>(), new ArrayList<>());
        router = new ChannelRouter();
        deliveryLog = Mockito.mock(MsgDeliveryLogMapper.class);
        ReflectionTestUtils.setField(router, "senderRegistry", registry);
        ReflectionTestUtils.setField(router, "properties", messageProperties);
        ReflectionTestUtils.setField(router, "rateLimiter", new ChannelRateLimiter(1000));
        ReflectionTestUtils.setField(router, "templateEngine", Mockito.mock(MessageTemplateEngine.class));
        ReflectionTestUtils.setField(router, "preferenceService", Mockito.mock(MsgUserPreferenceService.class));
        ReflectionTestUtils.setField(router, "deliveryLogMapper", deliveryLog);
    }

    private static Message dingMsg() {
        return Message.builder().channel(Channels.IM_DINGTALK).bizType("BUILD_DONE")
                .subject("构建完成").content("分支 main 构建成功").build();
    }

    @Test
    void missingConfigMakesRouterSkipWithoutTouchingNetwork() {
        dingTalk = new DingTalkRobotSender(channelsProps, new SimpleHttpClient()); // 什么都没配
        wire(new ArrayList<>(java.util.Arrays.asList((ChannelSender) dingTalk)));
        // yml 等价物：z-msg.channel.im_dingtalk.provider=robot
        MessageProperties.Channel ch = new MessageProperties.Channel();
        ch.setProvider("robot");
        messageProperties.getChannel().put("im_dingtalk", ch);

        assertFalse(dingTalk.ready(), "前提：配置不齐");

        MessageSendResult r = router.route(dingMsg());

        assertFalse(r.isSuccess());
        assertEquals("PROVIDER_NOT_CONFIGURED", r.getErrorCode());
        assertEquals(0, stub.all().size(), "未配置的 provider 不许发出任何 HTTP");

        // 投递日志里这一行的 provider 列必须填上：它存在的意义就是"哪个 provider 没配齐"，
        // 空着的话运维按 provider 分组统计恰好漏掉最该看的那一类
        ArgumentCaptor<MsgDeliveryLogDO> row = ArgumentCaptor.forClass(MsgDeliveryLogDO.class);
        Mockito.verify(deliveryLog).insert(row.capture());
        assertEquals("robot", row.getValue().getProvider(),
                "PROVIDER_NOT_CONFIGURED 行的 provider 列为空 = 查不出是哪个 provider 没配");
    }

    @Test
    void configuredProviderIsActuallyInvokedThroughUnmodifiedRouter() {
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        cfg.setToken("tok-router");
        channelsProps.put(Channels.IM_DINGTALK, cfg);
        dingTalk = new DingTalkRobotSender(channelsProps, new SimpleHttpClient());
        wire(new ArrayList<>(java.util.Arrays.asList((ChannelSender) dingTalk)));
        MessageProperties.Channel ch = new MessageProperties.Channel();
        ch.setProvider("robot");
        messageProperties.getChannel().put("im_dingtalk", ch);
        stub.enqueue("/robot/send", 200, "{\"errcode\":0,\"errmsg\":\"ok\"}");

        assertTrue(dingTalk.ready(), "前提：配置齐");
        MessageSendResult r = router.route(dingMsg());

        assertTrue(r.isSuccess(), "同一条链路配上配置后必须真投出去（上一例 skip 的正面）: " + r.getErrorMessage());
        assertEquals(1, stub.countFor("/robot/send"));
        assertSame(dingTalk, registry.pick(Channels.IM_DINGTALK),
                "yml 的 provider 名应当选中这个 bean——router 源码一行没改");
    }

    @Test
    void newChannelNeedsOnlyABeanAndOneYmlKey() {
        DemoPushSender demo = new DemoPushSender();
        wire(new ArrayList<>(java.util.Arrays.asList((ChannelSender) demo)));
        MessageProperties.Channel ch = new MessageProperties.Channel();
        ch.setProvider("demo");
        messageProperties.getChannel().put("push_fcm", ch);

        Message m = Message.builder().channel(Channels.PUSH_FCM).receiver("device-token")
                .bizType("NEWS").subject("s").content("c").build();
        MessageSendResult r = router.route(m);

        assertTrue(r.isSuccess());
        assertEquals(1, demo.got.size());
        assertSame(demo, registry.pick(Channels.PUSH_FCM));
    }

    @Test
    void registryKeepsMockFallbackForUnregisteredChannels() {
        // 没配任何 IM provider 时 core 的兜底行为不受本模块影响（回归护栏）
        wire(new ArrayList<ChannelSender>());
        ChannelSender picked = registry.pick(Channels.IM_DINGTALK);
        assertTrue(picked.isMock());
        assertEquals("mock", picked.provider());
    }
}
