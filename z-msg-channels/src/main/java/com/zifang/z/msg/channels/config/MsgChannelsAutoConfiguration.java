package com.zifang.z.msg.channels.config;

import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.channels.http.SimpleHttpClient;
import com.zifang.z.msg.channels.provider.AliyunSmsSender;
import com.zifang.z.msg.channels.provider.DingTalkRobotSender;
import com.zifang.z.msg.channels.provider.FeishuRobotSender;
import com.zifang.z.msg.channels.provider.RecordingMockSender;
import com.zifang.z.msg.channels.provider.SlackBotSender;
import com.zifang.z.msg.channels.provider.WeixinMpSender;
import com.zifang.z.msg.channels.provider.WecomRobotSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * z-msg-channels 自动装配（spring.factories 注册，宿主零代码接入）。
 * <p>
 * 装配策略（依据 core {@code ChannelRouter} 的实测行为）：
 * <b>provider bean 无条件装配，配置不齐靠 {@code ready()=false} 让 router 记
 * PROVIDER_NOT_CONFIGURED 跳过</b>，而不是"没配就不装配"。理由：
 * {@code SenderRegistry.pick} 对"channel 配了 provider 名但 bean 不存在"会回落到
 * mock 兜底（status=3 的假成功）；若这里按配置条件装配，宿主写了
 * {@code provider: robot} 却拼错 key 时，链路表现是"mock 成功"而不是"配置不完整"，
 * 排查方向直接反了。bean 常驻则错误配置一定在投递日志里显形。
 * bean 本身不发起任何网络调用，常驻无副作用。
 * <p>
 * 不依赖 z-msg-web / z-msg-ws，也不按名字引用 core 的任何 bean：
 * 只吃 {@link ChannelsProperties}，注册为普通 {@code ChannelSender} bean 后由
 * core 的 {@code SenderRegistry}（ObjectProvider&lt;ChannelSender&gt;）自动收集——
 * 这就是"加一个新渠道 = 一个 ChannelSender bean + 一段 yml"的兑现点。
 */
@Configuration
@ConditionalOnProperty(prefix = "z-msg", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ChannelsProperties.class)
public class MsgChannelsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SimpleHttpClient zMsgChannelsHttpClient() {
        return new SimpleHttpClient();
    }

    @Bean
    @ConditionalOnMissingBean(DingTalkRobotSender.class)
    public DingTalkRobotSender dingTalkRobotSender(ChannelsProperties props, SimpleHttpClient http) {
        return new DingTalkRobotSender(props, http);
    }

    @Bean
    @ConditionalOnMissingBean(FeishuRobotSender.class)
    public FeishuRobotSender feishuRobotSender(ChannelsProperties props, SimpleHttpClient http) {
        return new FeishuRobotSender(props, http);
    }

    @Bean
    @ConditionalOnMissingBean(WecomRobotSender.class)
    public WecomRobotSender wecomRobotSender(ChannelsProperties props, SimpleHttpClient http) {
        return new WecomRobotSender(props, http);
    }

    @Bean
    @ConditionalOnMissingBean(SlackBotSender.class)
    public SlackBotSender slackBotSender(ChannelsProperties props, SimpleHttpClient http) {
        return new SlackBotSender(props, http);
    }

    @Bean
    @ConditionalOnMissingBean(WeixinMpSender.class)
    public WeixinMpSender weixinMpSender(ChannelsProperties props, SimpleHttpClient http) {
        return new WeixinMpSender(props, http);
    }

    @Bean
    @ConditionalOnMissingBean(AliyunSmsSender.class)
    public AliyunSmsSender aliyunSmsSender(ChannelsProperties props, SimpleHttpClient http) {
        return new AliyunSmsSender(props, http);
    }

    // ===== 录制 mock（provider=mock 时接管；默认 provider 缺省名也是 mock，
    // 所以未配置 provider 的机器人通道会落在这里，比 core 的 MockFallbackSender
    // 多给一份"请求内容可见"，本地开发和 example 用 =====

    @Bean(name = "recordingMockDingTalk")
    @ConditionalOnMissingBean(name = "recordingMockDingTalk")
    public RecordingMockSender recordingMockDingTalk() {
        return new RecordingMockSender(Channels.IM_DINGTALK);
    }

    @Bean(name = "recordingMockFeishu")
    @ConditionalOnMissingBean(name = "recordingMockFeishu")
    public RecordingMockSender recordingMockFeishu() {
        return new RecordingMockSender(Channels.IM_FEISHU);
    }

    @Bean(name = "recordingMockWecom")
    @ConditionalOnMissingBean(name = "recordingMockWecom")
    public RecordingMockSender recordingMockWecom() {
        return new RecordingMockSender(Channels.IM_WECOM);
    }

    @Bean(name = "recordingMockSlack")
    @ConditionalOnMissingBean(name = "recordingMockSlack")
    public RecordingMockSender recordingMockSlack() {
        return new RecordingMockSender(Channels.IM_SLACK);
    }
}
