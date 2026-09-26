package com.zifang.z.msg.web;

import org.h2.jdbcx.JdbcDataSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * z-msg-web 全部 {@code @SpringBootTest} 共用的上下文。
 * <p>
 * 这个包里<b>只允许有这一处</b> {@code @Configuration}：{@code MsgAutoConfiguration} 的
 * {@code @ComponentScan("com.zifang.z.msg.web")} 扫的是 classpath，test-classes 也在里面，
 * 于是每个测试类各自写一个内嵌 TestApp 就会把 {@code dataSourceMsg} 注册三遍 ——
 * 第二个上下文起不来，报 BeanDefinitionOverrideException，而且报的是"别人的 TestApp"，
 * 单看堆栈很容易以为是自己改坏了。
 * <p>
 * 库名从 {@code z.msg.test.db} 取：各测试类给一个自己的名字，上下文之间就不共用同一座内存 H2
 * （H2 的 mem 库是进程级全局的，而 {@code @SpringBootTest} 的上下文会缓存到 JVM 结束）。
 * {@code MODE=MySQL} 是为了让生产那份 {@code PaginationInnerInterceptor(DbType.MYSQL)} 原样跑通，
 * 而不是在测试里换一套方言骗过自己。
 */
@Configuration
@EnableAutoConfiguration
public class MsgWebTestApplication {

    @Bean(name = "dataSourceMsg")
    @ConditionalOnMissingBean(name = "dataSourceMsg")
    public DataSource dataSourceMsg(Environment env) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + env.getProperty("z.msg.test.db", "msg_web_it")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
                + ";INIT=RUNSCRIPT FROM 'classpath:z-msg/sql/schema-h2.sql'");
        ds.setUser("sa");
        return ds;
    }
}
