package com.zifang.z.msg.web.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zifang.z.boot.datasource.starter.ModuleDataSourceTemplate;
import com.zifang.z.msg.core.bus.DefaultMessageBus;
import com.zifang.z.msg.core.channel.InAppChannel;
import com.zifang.z.msg.core.domain.mapper.InAppMessageMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@ComponentScan("com.zifang.z.msg")
@MapperScan(
        basePackages = "com.zifang.z.msg.core.domain.mapper",
        sqlSessionFactoryRef = "sqlSessionFactoryMsg"
)
public class MsgAutoConfiguration extends ModuleDataSourceTemplate {

    @Bean(name = "dataSourceMsg")
    public DataSource dataSourceMsg(Environment env) {
        return buildDataSource(env, "msg");
    }

    @Bean(name = "sqlSessionFactoryMsg")
    public SqlSessionFactory sqlSessionFactoryMsg(
            @org.springframework.beans.factory.annotation.Qualifier("dataSourceMsg")
            DataSource dataSourceMsg) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSourceMsg);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/**/*.xml"));
        return factoryBean.getObject();
    }

    @Bean
    public DefaultMessageBus defaultMessageBus() {
        return new DefaultMessageBus();
    }

    @Bean
    public InAppChannel inAppChannel(DefaultMessageBus bus,
                                     InAppMessageMapper mapper,
                                     com.zifang.z.msg.core.template.MessageTemplateEngine templateEngine) {
        InAppChannel channel = new InAppChannel(mapper, templateEngine);
        bus.registerGlobal(channel);
        return channel;
    }

    /**
     * 异步线程池 — 供 @Async 批量发送使用
     */
    @Bean(name = "msgAsyncExecutor")
    public Executor msgAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("msg-async-");
        executor.initialize();
        return executor;
    }
}
