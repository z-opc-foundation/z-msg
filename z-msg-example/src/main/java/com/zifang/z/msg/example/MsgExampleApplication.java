package com.zifang.z.msg.example;

import org.h2.jdbcx.JdbcDataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * 五分钟聊天室 + 站内信：一个真的、能 {@code mvn spring-boot:run} 起来的宿主。
 * <p>
 * 这个类存在的意义是"宿主侧到底要写多少代码"这个问题的可执行答案：
 * 答案是 —— 一把 ticket 密钥、一个数据源、一个身份接缝、一个 topic 策略，
 * 剩下的（握手、帧、授权串联、站内信落库后推在线连接）都是引 jar 就有的。
 * <p>
 * 它不在发布清单里（parent 的 {@code <skip>} 与 {@code deploy_maven_center.sh} 都不带它），
 * 所以可以放心用内存库和演示用的登录态。
 */
@SpringBootApplication
public class MsgExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsgExampleApplication.class, args);
    }

    /**
     * 演示用内存库，顶掉 {@code z-msg-web} 里那台 MySQL/Druid 的 {@code dataSourceMsg}：
     * 那个 bean 带 {@code @ConditionalOnMissingBean(name = "dataSourceMsg")}，
     * 宿主先注册就轮不到它。表结构直接执行打进 jar 的那两份 DDL，
     * 所以这里跑的就是 {@code SchemaParityTest} 守护的同一份脚本，不是另写一份演示专用表。
     * <p>
     * 接生产只要删掉这个方法、改成配 {@code z.base.db.msg.*}，业务代码一行不动。
     */
    @Bean(name = "dataSourceMsg")
    public DataSource dataSourceMsg() {
        JdbcDataSource ds = new JdbcDataSource();
        // 一个 INIT 里放多条语句要用 \; 分隔：URL 本身以 ; 分隔属性，裸 ; 会被当成下一个属性
        ds.setURL("jdbc:h2:mem:msg_example;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
                        + ";INIT=RUNSCRIPT FROM 'classpath:z-msg/sql/schema-h2.sql'"
                        + "\\;RUNSCRIPT FROM 'classpath:z-msg/sql/im-schema-h2.sql'");
        ds.setUser("sa");
        return ds;
    }
}
