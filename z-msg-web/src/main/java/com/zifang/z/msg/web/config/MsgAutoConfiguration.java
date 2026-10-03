package com.zifang.z.msg.web.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zifang.z.boot.datasource.starter.ModuleDataSourceTemplate;
import com.zifang.z.msg.core.bus.DefaultMessageBus;
import com.zifang.z.msg.core.channel.InAppChannel;
import com.zifang.z.msg.core.config.MessageProperties;
import com.zifang.z.msg.web.auth.MsgPrincipalResolver;
import com.zifang.z.msg.web.auth.TrustedHeaderPrincipalResolver;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;

/**
 * z-msg-web 自动配置
 * <p>
 * 1.1.0 变更（都是能跑出错误行为的真问题）：
 * <ul>
 *   <li>{@code z-msg.enabled=false} 以前根本没人判断，现在整套 bean 受条件控制；</li>
 *   <li>{@code MessageProperties} 以前要宿主手动 @EnableConfigurationProperties，
 *       只引 core 的工程拿不到配置；</li>
 *   <li>sqlSessionFactory 以前没挂 {@code PaginationInnerInterceptor}，
 *       所有 {@code selectPage} 静默返回全表——分页参数看着生效、其实一条没截；</li>
 *   <li>{@code dataSourceMsg} 允许宿主覆盖（测试/嵌库场景给 H2）；</li>
 *   <li>组件扫描范围从 {@code com.zifang.z.msg} 收窄到 core+web，
 *       否则宿主把示例工程放进 classpath 就会连 example 的 bean 一起扫进生产。</li>
 * </ul>
 * 另外 1.1.0 起了管理面默认关：{@link AdminEndpointGate} 在
 * {@code z-msg.web.admin-endpoints-enabled} 缺省时把 template/batch/delivery 三组前缀挡成 403。
 * core 层的 provider 注册表、限流器、实时发布器、异步线程池在
 * {@code MsgCoreConfiguration} 里，不在本类重复声明。
 */
@Configuration
@ConditionalOnProperty(prefix = "z-msg", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({MessageProperties.class, MsgWebProperties.class})
@ComponentScan(
        basePackages = {"com.zifang.z.msg.core", "com.zifang.z.msg.web"},
        // 2026-10-03 实测纠错 (z-team standalone team-1 起不来的直接原因):
        // com.zifang.z.msg.web.host 是本扫描根 com.zifang.z.msg.web 的**子包**, 于是这里
        // 把它一并吞掉 —— 但那个子包按设计归 MsgHostAutoConfiguration 独占, 受
        // z.msg.host.enabled 门控(standalone 不开)。归属被本扫描破坏后:
        //   1) MsgHostAutoConfiguration 虽然在条件报告里是 Negative match, 类还是被注册了;
        //   2) 更硬的是 MsgCodeChannelSenders 实现的 CodeChannelSender SPI 来自 z-ctc-web,
        //      而 z-msg-web 对该依赖声明 scope=provided(注释:「host 桥接类 … 运行期由宿主供」),
        //      standalone 的 fat jar 里没有那个 class。ASM 读它的元数据就直接炸:
        //      FileNotFoundException: class path resource
        //      [com/zifang/ctc/web/service/CodeChannelSender.class] cannot be opened
        // 修法是划清边界(排除), 不是给 standalone 补 z-ctc-web 依赖 ——
        // 那等于让所有 z-msg 使用方无条件背上 z-ctc-web。
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.zifang\\.z\\.msg\\.web\\.host(\\..*)?"),
        })
@MapperScan(
        basePackages = "com.zifang.z.msg.core.domain.mapper",
        sqlSessionFactoryRef = "sqlSessionFactoryMsg"
)
public class MsgAutoConfiguration extends ModuleDataSourceTemplate {

    @Bean(name = "dataSourceMsg")
    @ConditionalOnMissingBean(name = "dataSourceMsg")
    public DataSource dataSourceMsg(Environment env) {
        return buildDataSource(env, "msg");
    }

    @Bean(name = "msgMybatisPlusInterceptor")
    @ConditionalOnMissingBean(name = "msgMybatisPlusInterceptor")
    public MybatisPlusInterceptor msgMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件必须显式声明方言：没有它 selectPage 会把 limit 当成 hint 忽略掉
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean(name = "sqlSessionFactoryMsg")
    @ConditionalOnMissingBean(name = "sqlSessionFactoryMsg")
    public SqlSessionFactory sqlSessionFactoryMsg(
            @Qualifier("dataSourceMsg") DataSource dataSourceMsg,
            @Qualifier("msgMybatisPlusInterceptor") MybatisPlusInterceptor interceptor) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSourceMsg);
        factoryBean.setPlugins(interceptor);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/**/*.xml"));
        return factoryBean.getObject();
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultMessageBus defaultMessageBus() {
        return new DefaultMessageBus();
    }

    /**
     * 身份解析。宿主注册自己的 {@link com.zifang.z.msg.web.auth.MsgPrincipalResolver} bean 就顶掉这个
     * （z-ctc 的 JWT 解析应当这么做）；没有则用可信头实现，而它默认不开 ——
     * 于是收件箱/偏好的写读全部 401，而不是退回去相信客户端自报的 userId。
     */
    @Bean
    @ConditionalOnMissingBean(MsgPrincipalResolver.class)
    public MsgPrincipalResolver msgPrincipalResolver(MsgWebProperties webProperties) {
        return new TrustedHeaderPrincipalResolver(
                webProperties.isTrustedHeaderEnabled(), webProperties.getTrustedHeaderName());
    }

    /**
     * 管理面闸门。默认关（{@link MsgWebProperties#isAdminEndpointsEnabled()}），见 {@link AdminEndpointGate}。
     */
    @Bean
    @ConditionalOnMissingBean
    public AdminEndpointGate adminEndpointGate(MsgWebProperties webProperties) {
        return new AdminEndpointGate(webProperties);
    }

    /**
     * 路径挂 {@code /**} 而不是逐个前缀：拦不拦只由 {@link AdminEndpointGate#isAdminPath} 一处判定，
     * 少一份会跟 ADMIN_PREFIXES 漂移的重复配置。
     */
    @Bean
    public WebMvcConfigurer msgAdminEndpointConfigurer(AdminEndpointGate adminEndpointGate) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(adminEndpointGate).addPathPatterns("/**");
            }
        };
    }

    /**
     * 把站内信通道挂成总线全局 handler：任何 publish 都留一条站内信底账。
     * InAppChannel 本身由 core 建成 provider bean，这里只做绑定，避免两处 new 出两份。
     */
    @Bean
    public MsgBusBindings msgBusBindings(DefaultMessageBus bus, InAppChannel inAppChannel) {
        return new MsgBusBindings(bus, inAppChannel);
    }

    /**
     * 小而明确的一次性绑定器：构造时注册，不做任何隐式生命周期动作
     */
    public static class MsgBusBindings {
        public MsgBusBindings(DefaultMessageBus bus, InAppChannel inAppChannel) {
            bus.registerGlobal(inAppChannel);
        }
    }
}
