package com.zifang.z.msg.im.config;

import com.zifang.z.msg.im.domain.entity.ImConversationDO;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * z-msg-im 自动配置：宿主把 jar 放进 classpath 就得到一整套 IM 能力
 * （会话/消息/已读服务 + 4 个 mapper + REST 接口 + {@code room:} 订阅授权），
 * 不需要在宿主自己的启动类上写 {@code @MapperScan} 或 {@code @Import}。
 * <p>
 * 为什么必须是自动装配而不是"让宿主扫一下"：im 的 mapper 在
 * {@code com.zifang.z.msg.im.domain.mapper}，而 {@code MsgAutoConfiguration} 里那一条
 * {@code @MapperScan} 的 basePackages 只写了 {@code com.zifang.z.msg.core.domain.mapper}，
 * 它的 {@code @ComponentScan} 也只有 core + web —— 两个都不覆盖 im。
 * 也就是说宿主不手工接线的话，本模块一个 bean 都不会有，而表现是
 * {@code NoSuchBeanDefinitionException}（在第一次用到 service 时），不是启动期报错。
 * 这种"装了包却静默不可用"是最难查的一类集成问题，所以接线责任在本模块。
 * <p>
 * <b>退避条件为什么不是 {@code @ConditionalOnBean(name = "sqlSessionFactoryMsg")}</b>
 * （这条是被实测推翻后改掉的，不是没想到）：本类自己一个 {@code @Bean} 方法都没有，
 * 因此它压根不出现在 {@code spring-autoconfigure-metadata.properties} 里，
 * {@code OnBeanCondition} 作为 import filter 无法在早期把它筛掉；等到
 * {@code ConfigurationClassPostProcessor} 解析到它时，<strong>任何</strong>自动配置类的
 * {@code @Bean} 方法都还没注册 bean definition —— 那一刻
 * {@code sqlSessionFactoryMsg} 不存在，条件判 false，而且这个 false 是决定性的。
 * 同一份 {@code ConditionEvaluationReport} 里既能看到
 * "did not find any beans named sqlSessionFactoryMsg"，又能看到
 * "found bean 'sqlSessionFactoryMsg'"；用注册序号量出来的真实顺序是
 * core {@code realtimePublisher}=154、web {@code msgMybatisPlusInterceptor}=159、
 * web {@code sqlSessionFactoryMsg}=160、im 自己扫出来的 bean 在 167 之后，
 * 也就是说<strong>排序不是原因</strong>：{@code @AutoConfigureAfter} 无论 name 形式还是
 * 类引用形式都生效了，条件仍然为 false；把守卫挪到一个嵌套 {@code @Configuration} 上同理。
 * <p>
 * 现在这组条件的存在理由（语义要求："宿主没引 web"或"开关关着"时必须干净退避）：
 * <ul>
 *   <li>{@code @ConditionalOnClass(name = "com.zifang.z.msg.web.config.MsgAutoConfiguration")}：
 *       {@code sqlSessionFactoryMsg} 只有一个来源，就是 z-msg-web 的这套自动配置。
 *       类在不在 classpath 是一个<strong>装配期就能确定</strong>的事实，不受上面那个时序问题影响，
 *       所以用它的存在与否代表"有没有人建 sqlSessionFactoryMsg"；</li>
 *   <li>{@code @ConditionalOnProperty(prefix = "z-msg", name = {"enabled", "im.enabled"})}：
 *       两个名字一起写是因为 {@code @ConditionalOnProperty} 不可重复。
 *       {@code z-msg.enabled=false} 时 web 根本不会建 {@code sqlSessionFactoryMsg}，
 *       im 必须跟着退避（这正是 web 自己 obeys 的那个开关）；
 *       {@code z-msg.im.enabled=false} 只关 im，不影响站内信。
 *       两条都由 {@code ImDisabledAutoConfigurationTest} 从条件评估报告里当场验；</li>
 *   <li>{@code @AutoConfigureAfter(name = ...)}：用 <em>name</em> 形式而不是类引用形式，
 *       这样不产生对 {@code MsgAutoConfiguration} 的编译期符号引用。它保证的是
 *       web 的 {@code @MapperScan}／拦截器先落定，im 这一条 {@code @MapperScan}
 *       再按名字去引用 {@code sqlSessionFactoryMsg}。
 *       代价说清楚：本模块的 pom <strong>确实</strong> compile 依赖了 z-msg-web，
 *       但不是为了这个注解，而是因为 controller 要用 {@code MsgPrincipalResolver}
 *       （身份只能来自服务端解出的凭证）。这条 compile 依赖带来的是：
 *       只想要 IM 领域层、不想被拖进 servlet 世界的宿主会连带拿到 web 的 controller、
 *       {@code z-boot-web-starter} 和 mail starter；而 im 与 web 的发布版本必须同步，
 *       web 的 {@code sqlSessionFactoryMsg} 这个 bean 名也因此成了 im 的契约。
 *       反过来如果不依赖 web，controller 就无法存在（没有身份来源），
 *       只能退化成"只给领域层、REST 让宿主自己写"。</li>
 *   <li>{@code @EnableConfigurationProperties(ImProperties.class)}：让
 *       {@code z-msg.im.*} 那组旋钮在没有任何手工接线的情况下就能绑定；</li>
 *   <li>{@code @ComponentScan}：服务层、授权策略、controller 一起进来。
 *       三个包逐个点名而不是写 {@code com.zifang.z.msg.im} 整根 ——
 *       扫整根会把本类自己也扫进来（它是 {@code @Configuration}，即 {@code @Component}），
 *       同一个自动配置类被注册两遍、{@code @MapperScan} 的 post-processor 也跑两遍。
 *       扫描范围始终在 {@code com.zifang.z.msg.im.*} 内，不会越界去扫宿主的包。</li>
 * </ul>
 * <p>
 * 这套替代方案的<strong>已知不完美</strong>（别把它读成"什么都能退避"）：守卫看的是
 * "类在不在 + 开关开不开"，不是"bean 在不在"。所以宿主自己写一套同名自动配置、
 * 或者把 {@code sqlSessionFactoryMsg} 改名／换成自己的一批，本类仍然会装配，
 * 失败点回到第一次用到 mapper 时的 {@code NoSuchBeanDefinitionException}。
 * 想真正按 bean 判定，只能把 im 的装配挂到一个有 {@code @Bean} 方法、
 * 因此出现在 metadata 快路径里的配置类上（例如由 core/web 用一个
 * {@code @Bean} 工厂方法来 {@code @Import} im）——那是跨模块的接线改动，不归本模块决定。
 *
 * @see ImConversationDO 实体与表结构的对应关系见 {@code z-msg/sql/im-schema-*.sql}
 */
@Configuration
@ConditionalOnProperty(prefix = "z-msg", name = {"enabled", "im.enabled"}, havingValue = "true",
        matchIfMissing = true)
@ConditionalOnClass(name = "com.zifang.z.msg.web.config.MsgAutoConfiguration")
@AutoConfigureAfter(name = {
        "com.zifang.z.msg.web.config.MsgAutoConfiguration"
})
@EnableConfigurationProperties(ImProperties.class)
@ComponentScan(basePackages = {
        "com.zifang.z.msg.im.domain.service",
        "com.zifang.z.msg.im.auth",
        "com.zifang.z.msg.im.api"
})
@MapperScan(
        basePackages = "com.zifang.z.msg.im.domain.mapper",
        sqlSessionFactoryRef = "sqlSessionFactoryMsg"
)
public class MsgImAutoConfiguration {
}
