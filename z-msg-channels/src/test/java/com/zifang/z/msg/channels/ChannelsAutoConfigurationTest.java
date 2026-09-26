package com.zifang.z.msg.channels;

import com.zifang.z.msg.channels.config.ChannelsProperties;
import com.zifang.z.msg.channels.config.MsgChannelsAutoConfiguration;
import com.zifang.z.msg.channels.http.SimpleHttpClient;
import com.zifang.z.msg.channels.provider.AliyunSmsSender;
import com.zifang.z.msg.channels.provider.DingTalkRobotSender;
import com.zifang.z.msg.channels.provider.FeishuRobotSender;
import com.zifang.z.msg.channels.provider.JPushSender;
import com.zifang.z.msg.channels.provider.RecordingMockSender;
import com.zifang.z.msg.channels.provider.SlackBotSender;
import com.zifang.z.msg.channels.provider.TencentSmsSender;
import com.zifang.z.msg.channels.provider.WeixinMpSender;
import com.zifang.z.msg.channels.provider.WecomRobotSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动装配三件事：
 * 1) spring.factories 文件名与类名对得上（不依赖 web/ws）；
 * 2) yml（kebab-case）绑进 ChannelsProperties（Map 形态、三种 key 写法归一）；
 * 3) 每个 provider @ConditionalOnMissingBean，宿主自定义 bean 可顶掉。
 */
class ChannelsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MsgChannelsAutoConfiguration.class));

    @Test
    void springFactoriesRegistersTheAutoConfiguration() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/META-INF/spring.factories")) {
            assertNotNull(in, "z-msg-channels 必须自带 spring.factories");
            String text = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
            assertTrue(text.contains(MsgChannelsAutoConfiguration.class.getName()),
                    "spring.factories 应注册 " + MsgChannelsAutoConfiguration.class.getName() + "，实际: " + text);
        }
    }

    @Test
    void allProvidersRegisteredAndNotReadyWithoutConfig() {
        runner.run(ctx -> {
            // bean 常驻（skip 语义由 ready()=false 提供，见 MsgChannelsAutoConfiguration 类注释）
            assertNotNull(ctx.getBean(DingTalkRobotSender.class));
            assertNotNull(ctx.getBean(FeishuRobotSender.class));
            assertNotNull(ctx.getBean(WecomRobotSender.class));
            assertNotNull(ctx.getBean(SlackBotSender.class));
            assertNotNull(ctx.getBean(WeixinMpSender.class));
            assertNotNull(ctx.getBean(AliyunSmsSender.class));
            assertNotNull(ctx.getBean(TencentSmsSender.class));
            assertNotNull(ctx.getBean(JPushSender.class));
            assertFalse(ctx.getBean(DingTalkRobotSender.class).ready());
            assertFalse(ctx.getBean(SlackBotSender.class).ready());
            assertFalse(ctx.getBean(AliyunSmsSender.class).ready());
            assertFalse(ctx.getBean(TencentSmsSender.class).ready());
            assertFalse(ctx.getBean(JPushSender.class).ready());
            // 同属 SMS 的两个 sender 共存，靠 provider 名区分（pick() 按 "CHANNEL|provider" 索引）
            AliyunSmsSender aliyun = ctx.getBean(AliyunSmsSender.class);
            TencentSmsSender tencent = ctx.getBean(TencentSmsSender.class);
            assertEquals(aliyun.channel(), tencent.channel());
            assertEquals("aliyun", aliyun.provider());
            assertEquals("tencent", tencent.provider());
            // 录制 mock：provider 名 mock、isMock=true（ChannelSender 契约第二条）
            RecordingMockSender mock = ctx.getBean("recordingMockDingTalk", RecordingMockSender.class);
            assertTrue(mock.isMock());
            assertEquals("mock", mock.provider());
        });
    }

    @Test
    void kebabCaseYamlBindsToChannelCfg() {
        runner.withPropertyValues(
                "z-msg.channel.im-dingtalk.token=tok-kebab",
                "z-msg.channel.im-dingtalk.secret=sec-kebab",
                "z-msg.channel.im-slack.token=xoxb-kebab",
                "z-msg.channel.sms.access-key-id=AKID",
                "z-msg.channel.sms.access-key-secret=AKSEC",
                "z-msg.channel.im-weixin-mp.app-id=wxapp",
                "z-msg.channel.im-weixin-mp.app-secret=wxsec",
                "z-msg.channel.sms.sdk-app-id=1400009100",
                "z-msg.channel.push-jpush.app-key=jpush-app-key",
                "z-msg.channel.push-jpush.master-secret=jpush-master-secret",
                "z-msg.channel.sms.connect-timeout-ms=1234",
                "z-msg.channel.sms.read-timeout-ms=2345"
        ).run(ctx -> {
            assertTrue(ctx.getBean(DingTalkRobotSender.class).ready());
            assertTrue(ctx.getBean(SlackBotSender.class).ready());
            assertTrue(ctx.getBean(AliyunSmsSender.class).ready());
            assertTrue(ctx.getBean(WeixinMpSender.class).ready());
            assertTrue(ctx.getBean(TencentSmsSender.class).ready(),
                    "sms.sdk-app-id + access-key-id/secret 齐了，腾讯云这条就该 ready");
            assertTrue(ctx.getBean(JPushSender.class).ready());
            ChannelsProperties props = ctx.getBean(ChannelsProperties.class);
            assertEquals("tok-kebab", props.channel("IM_DINGTALK").getToken());
            assertEquals("sec-kebab", props.channel("im_dingtalk").getSecret());
            assertEquals("1400009100", props.channel("SMS").getSdkAppId());
            assertEquals("jpush-app-key", props.channel("PUSH_JPUSH").getAppKey());
            assertEquals("jpush-master-secret", props.channel("push_jpush").getMasterSecret());
            assertEquals(1234, props.channel("SMS").connectTimeoutMsOrDefault());
            assertEquals(2345, props.channel("SMS").readTimeoutMsOrDefault());
        });
    }

    @Test
    void upperCaseChannelKeyAlsoBinds() {
        runner.withPropertyValues("z-msg.channel.IM_FEISHU.token=hook-tok")
                .run(ctx -> assertTrue(ctx.getBean(FeishuRobotSender.class).ready()));
    }

    @Test
    void hostCanOverrideProviderBean() {
        runner.withUserConfiguration(CustomDingTalkConfig.class).run(ctx -> {
            DingTalkRobotSender bean = ctx.getBean(DingTalkRobotSender.class);
            assertSame(CustomDingTalkConfig.OVERRIDE, bean, "@ConditionalOnMissingBean 应让位给宿主 bean");
            assertTrue(bean.ready(), "宿主 override 生效");
        });
    }

    @Configuration
    static class CustomDingTalkConfig {
        static final DingTalkRobotSender OVERRIDE = new DingTalkRobotSender(new ChannelsProperties(), new SimpleHttpClient()) {
            @Override
            protected boolean configured() {
                return true;
            }
        };

        @Bean
        DingTalkRobotSender dingTalkRobotSender() {
            return OVERRIDE;
        }
    }

    @Test
    void disabledSwitchRemovesAllProviderBeans() {
        runner.withPropertyValues("z-msg.enabled=false")
                .run(ctx -> assertTrue(ctx.getBeansOfType(DingTalkRobotSender.class).isEmpty(),
                        "z-msg.enabled=false 时本模块不该装配任何东西"));
    }
}
