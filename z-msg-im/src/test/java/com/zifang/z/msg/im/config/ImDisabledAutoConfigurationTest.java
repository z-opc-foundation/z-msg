package com.zifang.z.msg.im.config;

import com.zifang.z.msg.im.api.ImConversationController;
import com.zifang.z.msg.im.api.ImReadController;
import com.zifang.z.msg.im.auth.ImTopicAuthorizationPolicy;
import com.zifang.z.msg.im.domain.mapper.ImConversationMapper;
import com.zifang.z.msg.im.domain.service.ImConversationService;
import com.zifang.z.msg.im.domain.service.ImMessageService;
import com.zifang.z.msg.im.domain.service.ImReadService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code z-msg.im.enabled=false} 时本模块必须一个 bean 都不注册。
 * <p>
 * 为什么单独一个上下文：这个开关只能在装配期起作用，跑起来的容器里改不动。
 * 反向对照（默认配置下这些 bean 都在）在 {@link ImAutoConfigurationTest} 里；
 * "这个 0 确实是开关造成的"由 {@link #theOnlyConditionThatBackedOffIsTheSwitchItself()}
 * 从同一个上下文的条件评估报告里当场读出来。
 * <p>
 * 这里同样不写 {@code @ComponentScan}、不写 {@code @MapperScan}：
 * 关掉之后什么都没有了，才说明那些 bean 真的是自动装配登记进来的。
 */
@SpringBootTest(classes = ImDisabledAutoConfigurationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "z-msg.enabled=true",
        "z-msg.web.trusted-header-enabled=true",
        "z-msg.im.enabled=false"
})
public class ImDisabledAutoConfigurationTest {

    @Resource
    private ConfigurableApplicationContext context;

    /** 与主上下文同一套自动装配登记，只是换了开关；数据源单独一把库。 */
    @Configuration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean(name = "dataSourceMsg")
        public DataSource dataSourceMsg() {
            org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
            ds.setURL("jdbc:h2:mem:msg_im_disabled_" + System.nanoTime()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
                    + ";INIT=RUNSCRIPT FROM 'classpath:z-msg/sql/schema-h2.sql'"
                    + "\\;RUNSCRIPT FROM 'classpath:z-msg/sql/im-schema-h2.sql'");
            ds.setUser("sa");
            return ds;
        }
    }

    @Test
    public void turningTheModuleOffRegistersNoImBeansAtAll() {
        assertNotNull(context.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class),
                "前置：web 上下文本身是活的，否则下面的 0 没有意义");
        for (Class<?> type : new Class<?>[]{ImConversationService.class, ImMessageService.class,
                ImReadService.class, ImConversationMapper.class, ImTopicAuthorizationPolicy.class,
                ImConversationController.class, ImReadController.class, ImProperties.class}) {
            assertEquals(0, context.getBeanNamesForType(type).length,
                    type.getSimpleName() + " 在 enabled=false 时不该出现: "
                            + java.util.Arrays.toString(context.getBeanNamesForType(type)));
        }
        assertTrue(context.getBeanNamesForType(javax.sql.DataSource.class).length > 0,
                "对照：宿主自己的数据源仍然在，关的是 im 不是整套");
    }

    /**
     * 正向对照：上面那个"0 个 bean"必须是这个开关造成的，而不是"本模块压根没装配上任何东西"。
     * <p>
     * 判法不是再手工搭一套 bean 来比（那等于自己造答案），而是把<strong>同一个上下文</strong>
     * 的条件评估报告读出来：{@code MsgImAutoConfiguration} 必须真的被问到过，
     * 而且它身上<strong>只有</strong>一条条件没匹配上、那条必须是点名
     * {@code z-msg.im.enabled} 的 {@code @ConditionalOnProperty}。
     * 这等于当场证明"把开关拨回 true 就会装"——因为挡住它的只有这一个属性值。
     * 反向（开关为 true 时这些 bean 真的都在）由 {@link ImAutoConfigurationTest} 在真上下文里证。
     */
    @Test
    public void theOnlyConditionThatBackedOffIsTheSwitchItself() {
        ConditionEvaluationReport report =
                ConditionEvaluationReport.find(context.getBeanFactory());
        java.util.Map<String, ConditionEvaluationReport.ConditionAndOutcomes> bySource =
                report.getConditionAndOutcomesBySource();
        String source = MsgImAutoConfiguration.class.getName();
        ConditionEvaluationReport.ConditionAndOutcomes outcomes = bySource.get(source);
        assertNotNull(outcomes, source + " 压根没进过条件评估，那上面那个 0 就是别的原因造成的。"
                + "报告里的来源: " + bySource.keySet());

        List<String> missed = new ArrayList<String>();
        for (ConditionEvaluationReport.ConditionAndOutcome o : outcomes) {
            if (!o.getOutcome().isMatch()) {
                missed.add(o.getCondition().getClass().getSimpleName()
                        + " -> " + o.getOutcome().getConditionMessage());
            }
        }
        assertEquals(1, missed.size(), source + " 只该被开关这一条挡住，实际没匹配上的: " + missed);
        assertTrue(missed.get(0).contains("OnPropertyCondition"),
                "没匹配上的那条必须是 @ConditionalOnProperty，实际: " + missed);
        assertTrue(missed.get(0).contains("im.enabled"),
                "它必须点名 im.enabled（也就是 z-msg.im.enabled），实际: " + missed);

        // 对照：同一份报告里 web 的自动装配是全匹配的 —— 整条链子是活的，被关掉的只有 im
        ConditionEvaluationReport.ConditionAndOutcomes web =
                bySource.get("com.zifang.z.msg.web.config.MsgAutoConfiguration");
        assertNotNull(web, "对照：web 的自动装配也该被问到过，实际来源: " + bySource.keySet());
        assertTrue(web.isFullMatch(), "对照：web 没被关，条件应当全匹配，实际: " + web);
    }
}
