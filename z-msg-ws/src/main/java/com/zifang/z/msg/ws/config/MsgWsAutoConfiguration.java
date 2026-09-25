package com.zifang.z.msg.ws.config;

import com.zifang.z.msg.api.RealtimePublisher;
import com.zifang.z.msg.api.TopicAuthorizationPolicy;
import com.zifang.z.msg.core.realtime.RealtimeTicketService;
import com.zifang.z.msg.ws.auth.TopicAuthorizer;
import com.zifang.z.msg.ws.handler.MsgWebSocketHandler;
import com.zifang.z.msg.ws.handshake.MsgHandshakeInterceptor;
import com.zifang.z.msg.ws.session.WsSessionRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

import java.util.ArrayList;
import java.util.List;

/**
 * z-msg-ws 自动配置。
 * <p>
 * 宿主只要把本模块放进 classpath 并配好 {@code z-msg.realtime.ticket-secret}，
 * 就得到一条 {@code /api/msg/ws} 端点：不需要写 handler。
 * <p>
 * {@code @EnableWebSocket} 不能省：Spring Boot 2.7 只会为 WebSocket 注册
 * {@code WsSci}（容器侧）和 {@code ServletServerContainerFactoryBean} 这类基础设施，
 * <b>没有</b>把容器里的 {@link WebSocketConfigurer} 汇总到握手分发的自动配置——
 * 那个活是 {@code DelegatingWebSocketConfiguration} 干的，只能靠 {@code @EnableWebSocket} 引进来。
 * 少了它，端点注册了也没人映射，宿主拿到的是 404（本模块的第一版就是这么栽的）。
 * <p>
 * 两个条件的存在理由：
 * <ul>
 *   <li>{@code @ConditionalOnBean}：{@code RealtimePublisher} 与 {@code RealtimeTicketService}
 *       由 z-msg-core 的配置类提供。缺了它们本模块无从挂接，此时整套 bean 退避，
 *       而不是启动报 NoSuchBeanDefinition——只引 core 不用实时的工程不该被拖下水；</li>
 *   <li>{@code @AutoConfigureAfter(name=...)}：core / web 的配置类要先完成注册。
 *       用 name 形式而不是类引用，是为了不让 z-msg-ws 反向依赖 z-msg-web。</li>
 * </ul>
 */
@Configuration
@EnableWebSocket
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebSocketConfigurer.class)
@ConditionalOnProperty(prefix = "z-msg.ws", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean({RealtimePublisher.class, RealtimeTicketService.class})
@AutoConfigureAfter(name = {
        "com.zifang.z.msg.web.config.MsgAutoConfiguration",
        "com.zifang.z.msg.core.config.MsgCoreConfiguration"
})
@EnableConfigurationProperties(WsProperties.class)
public class MsgWsAutoConfiguration {

    private static final Logger log = LogManager.getLogger(MsgWsAutoConfiguration.class);

    /**
     * 连接池本身就是一个 {@code RealtimeTransport}，core 的
     * {@code DefaultRealtimePublisher} 会把所有 transport bean 收集起来，
     * 所以站内信落库后推给在线连接这一步不需要任何粘合代码。
     */
    @Bean
    @ConditionalOnMissingBean
    public WsSessionRegistry wsSessionRegistry(WsProperties properties) {
        return new WsSessionRegistry(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TopicAuthorizer topicAuthorizer(ObjectProvider<TopicAuthorizationPolicy> policies,
                                          WsProperties properties) {
        List<TopicAuthorizationPolicy> list = new ArrayList<TopicAuthorizationPolicy>();
        policies.orderedStream().forEach(list::add);
        if (log.isInfoEnabled()) {
            log.info("[z-msg-ws] 授权策略 {} 条，显式公共 topic {} 个",
                    list.size(), properties.getPublicTopics().size());
        }
        return new TopicAuthorizer(list, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MsgHandshakeInterceptor msgHandshakeInterceptor(RealtimeTicketService ticketService) {
        return new MsgHandshakeInterceptor(ticketService);
    }

    @Bean
    @ConditionalOnMissingBean
    public MsgWebSocketHandler msgWebSocketHandler(WsSessionRegistry registry,
                                                   TopicAuthorizer authorizer,
                                                   RealtimePublisher publisher,
                                                   WsProperties properties) {
        return new MsgWebSocketHandler(registry, authorizer, publisher, properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "msgWebSocketConfigurer")
    public WebSocketConfigurer msgWebSocketConfigurer(MsgWebSocketHandler handler,
                                                      MsgHandshakeInterceptor interceptor,
                                                      WsProperties properties) {
        return new MsgWebSocketConfigurer(handler, interceptor, properties);
    }

    /**
     * 把 {@code z-msg.ws.*} 里两个容器级参数真正落到 JSR-356 容器上。
     * 光有字段没人读的话，配置就是装饰。
     * <p>
     * 超时只在配了正数时才设：{@code maxSessionIdleTimeout=0} 在 Tomcat 里是"永不超时"，
     * 而且它是容器级默认值，会把同一容器里宿主自己的 WebSocket 端点一起改掉——库不该这么做。
     */
    @Bean
    @ConditionalOnMissingBean(ServletServerContainerFactoryBean.class)
    public ServletServerContainerFactoryBean msgServletServerContainer(WsProperties properties) {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(properties.getMaxTextMessageBytes());
        container.setMaxBinaryMessageBufferSize(properties.getMaxTextMessageBytes());
        if (properties.getIdleTimeoutSeconds() > 0) {
            container.setMaxSessionIdleTimeout(properties.getIdleTimeoutSeconds() * 1000L);
        }
        return container;
    }

    /**
     * 单独一个类而不是匿名内部类：Spring 按方法名生成代理，配置类里塞实现细节会让
     * {@code registerWebSocketHandlers} 的日志栈难以定位。
     */
    public static class MsgWebSocketConfigurer implements WebSocketConfigurer {

        private final WebSocketHandler handler;
        private final MsgHandshakeInterceptor interceptor;
        private final WsProperties properties;

        public MsgWebSocketConfigurer(WebSocketHandler handler, MsgHandshakeInterceptor interceptor,
                                      WsProperties properties) {
            this.handler = handler;
            this.interceptor = interceptor;
            this.properties = properties;
        }

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            WebSocketHandlerRegistration registration =
                    registry.addHandler(handler, properties.getPath()).addInterceptors(interceptor);
            List<String> origins = properties.getAllowedOrigins();
            if (origins != null && !origins.isEmpty()) {
                registration.setAllowedOrigins(origins.toArray(new String[0]));
            }
            if (log.isInfoEnabled()) {
                log.info("[z-msg-ws] 端点已注册 {}（origin={}，单连接 topic 上限={}）",
                        properties.getPath(),
                        origins == null || origins.isEmpty() ? "不限" : origins,
                        properties.getMaxTopicsPerConnection());
            }
        }
    }
}
