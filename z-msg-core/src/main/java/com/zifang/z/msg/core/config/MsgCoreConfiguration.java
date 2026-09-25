package com.zifang.z.msg.core.config;

import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.EmailSender;
import com.zifang.z.msg.api.RealtimePublisher;
import com.zifang.z.msg.api.RealtimeTransport;
import com.zifang.z.msg.api.SmsSender;
import com.zifang.z.msg.core.channel.InAppChannel;
import com.zifang.z.msg.core.domain.mapper.InAppMessageMapper;
import com.zifang.z.msg.core.ratelimit.ChannelRateLimiter;
import com.zifang.z.msg.core.realtime.DefaultRealtimePublisher;
import com.zifang.z.msg.core.sender.SenderRegistry;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * z-msg-core 自带 bean 装配 (1.1.0)
 * <p>
 * 之前这些依赖装配全指望 z-msg-web 的 {@code MsgAutoConfiguration}，而它里面
 * {@code MessageProperties} 又必须由宿主手动 {@code @EnableConfigurationProperties}
 * 才生效——只引 z-msg-core 的项目（z-ctc、z-qa 这类不走 web 层的）拿不到可用的 gateway。
 * 现在 core 自己把 provider 注册表、限流器、实时发布器、异步池建好，宿主只需要给数据源。
 */
@Configuration
@ConditionalOnProperty(prefix = "z-msg", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MsgCoreConfiguration {

    private static final Logger log = LogManager.getLogger(MsgCoreConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SenderRegistry senderRegistry(MessageProperties properties,
                                         ObjectProvider<ChannelSender> channelSenders,
                                         ObjectProvider<SmsSender> legacySms,
                                         ObjectProvider<EmailSender> legacyEmail) {
        List<ChannelSender> list = new ArrayList<>();
        channelSenders.orderedStream().forEach(list::add);
        List<SmsSender> sms = new ArrayList<>();
        legacySms.orderedStream().forEach(sms::add);
        List<EmailSender> email = new ArrayList<>();
        legacyEmail.orderedStream().forEach(email::add);
        return new SenderRegistry(properties, list, sms, email);
    }

    /**
     * 限流器读配置建桶，1.0.0 是 {@code new ChannelRateLimiter(50)} 硬编码
     */
    @Bean
    @ConditionalOnMissingBean
    public ChannelRateLimiter channelRateLimiter(MessageProperties properties) {
        MessageProperties.RateLimit rl = properties.getRateLimit();
        ChannelRateLimiter limiter = new ChannelRateLimiter(rl.getDefaultPermitsPerSecond());
        for (Map.Entry<String, Integer> e : rl.getPerChannel().entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                limiter.setPermits(com.zifang.z.msg.api.Channels.normalize(e.getKey()), e.getValue());
            }
        }
        log.info("[z-msg] 限流器就绪 default={} perChannel={}",
                rl.getDefaultPermitsPerSecond(), rl.getPerChannel());
        return limiter;
    }

    /**
     * 实时发布器：没有 z-msg-ws 时 transports 为空，publish 返回 0，不报错
     */
    @Bean
    @ConditionalOnMissingBean(RealtimePublisher.class)
    public RealtimePublisher realtimePublisher(ObjectProvider<RealtimeTransport> transports) {
        List<RealtimeTransport> list = new ArrayList<>();
        transports.orderedStream().forEach(list::add);
        log.info("[z-msg] RealtimePublisher 挂接 {} 个 transport", list.size());
        return new DefaultRealtimePublisher(list);
    }

    /**
     * ws-ticket 签发/校验。放在 core 而不是 web：签发方是 web 的 {@code /api/msg/inbox/ws-token}，
     * 校验方是 ws 的握手拦截器，两边都只依赖 core，不必互相引。
     */
    @Bean
    @ConditionalOnMissingBean
    public com.zifang.z.msg.core.realtime.RealtimeTicketService realtimeTicketService(MessageProperties properties) {
        com.zifang.z.msg.core.realtime.RealtimeTicketService svc =
                new com.zifang.z.msg.core.realtime.RealtimeTicketService(properties);
        if (!svc.isConfigured()) {
            log.warn("[z-msg] z-msg.realtime.ticket-secret 未配置：ws-ticket 不会签发，"
                    + "WebSocket 只能靠宿主自己的 PrincipalResolver 认身份");
        }
        return svc;
    }

    @Bean
    @ConditionalOnMissingBean(name = "inAppChannel")
    public InAppChannel inAppChannel(InAppMessageMapper mapper,
                                     MessageTemplateEngine templateEngine,
                                     MessageProperties properties,
                                     ObjectProvider<RealtimePublisher> publisher) {
        InAppChannel channel = new InAppChannel(mapper, templateEngine, properties,
                publisher.getIfAvailable());
        return channel;
    }

    /**
     * 批量 / 异步任务线程池。之前只有 z-msg-web 里有这个 bean，
     * 而 {@code MsgBatchSendService} 现在显式依赖它，core 必须自带。
     */
    @Bean(name = "msgAsyncExecutor")
    @ConditionalOnMissingBean(name = "msgAsyncExecutor")
    public Executor msgAsyncExecutor(MessageProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("msg-async-");
        // 队列满了就丢给调用线程跑，而不是 AbortPolicy 抛异常把批量任务整批打挂
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
