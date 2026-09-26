package com.zifang.z.msg.im.config;

import com.zifang.z.msg.im.ImSpringTestSupport;
import com.zifang.z.msg.im.api.ImConversationController;
import com.zifang.z.msg.im.api.ImMessageController;
import com.zifang.z.msg.im.api.ImReadController;
import com.zifang.z.msg.im.auth.ImTopicAuthorizationPolicy;
import com.zifang.z.msg.im.domain.entity.ImReadReceiptDO;
import com.zifang.z.msg.im.domain.mapper.ImConversationMapper;
import com.zifang.z.msg.im.domain.mapper.ImMemberMapper;
import com.zifang.z.msg.im.domain.mapper.ImMessageMapper;
import com.zifang.z.msg.im.domain.mapper.ImReadReceiptMapper;
import com.zifang.z.msg.im.domain.service.ImConversationService;
import com.zifang.z.msg.im.domain.service.ImMessageService;
import com.zifang.z.msg.im.domain.service.ImReadService;
import com.zifang.z.msg.ws.auth.TopicAuthorizer;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 装配本身是不是对的。
 * <p>
 * 1.1.0 之前 im 的四张表连不上，症状不是启动失败而是运行期
 * {@code NoSuchBeanDefinitionException}：{@code MsgAutoConfiguration} 的
 * {@code @MapperScan} 只扫 {@code com.zifang.z.msg.core.domain.mapper}，
 * 而 {@code com.zifang.z.msg.im.domain.mapper} 在它的扫描范围之外。
 * 所以这一类的每条断言都必须"从容器里拿"，而不是自己 new 一个再证明它能用。
 */
public class ImAutoConfigurationTest extends ImSpringTestSupport {

    @Resource
    private ApplicationContext context;

    @Test
    public void springFactoriesAlonePutsEveryImBeanIntoTheContainer() {
        // service / policy / controller 都来自自动装配里的 @ComponentScan
        assertNotNull(context.getBean(ImConversationService.class));
        assertNotNull(context.getBean(ImMessageService.class));
        assertNotNull(context.getBean(ImReadService.class));
        assertNotNull(context.getBean(ImTopicAuthorizationPolicy.class));
        for (Class<?> controller : Arrays.<Class<?>>asList(ImConversationController.class,
                ImMessageController.class, ImReadController.class)) {
            assertEquals(1, context.getBeanNamesForType(controller).length, controller + " 应当被装配");
        }
        // 四张表四个 mapper，一个都不能漏
        for (Class<?> mapper : Arrays.<Class<?>>asList(ImConversationMapper.class, ImMemberMapper.class,
                ImMessageMapper.class, ImReadReceiptMapper.class)) {
            assertEquals(1, context.getBeanNamesForType(mapper).length, mapper + " 未被扫描: "
                    + Arrays.toString(context.getBeanNamesForType(mapper)));
        }
    }

    @Test
    public void imMappersAreRegisteredIntoTheSameSqlSessionFactoryAsCore() {
        // 按名字引用：这个 bean 名是 im 的 @MapperScan 与 web 之间的契约
        SqlSessionFactory factory = context.getBean("sqlSessionFactoryMsg", SqlSessionFactory.class);
        org.apache.ibatis.session.Configuration configuration = factory.getConfiguration();
        for (Class<?> mapper : Arrays.<Class<?>>asList(ImConversationMapper.class, ImMemberMapper.class,
                ImMessageMapper.class, ImReadReceiptMapper.class)) {
            assertTrue(configuration.hasMapper(mapper),
                    mapper + " 没注册进 sqlSessionFactoryMsg，用它的数据源就会找不到语句");
        }
        // core 的 mapper 也在同一个 factory 里：证明 im 是"加进那一个"，不是又开了一套
        assertTrue(configuration.hasMapper(com.zifang.z.msg.core.domain.mapper.InAppMessageMapper.class),
                "core 的 mapper 应当在同一个 SqlSessionFactory 里");
        assertSame(factory, context.getBean("sqlSessionFactoryMsg"), "同一个 bean 名只能有一个工厂");
    }

    @Test
    public void imPolicyIsWiredIntoTheRealAuthorizerAndKeepsPublicRoomsAlive() {
        // 这一条是"三态语义"在装配层的落地检查：策略必须被 TopicAuthorizer 问到，
        // 而且它对不认识的 room 不表态，宿主的 public-topics 才不会被本模块否掉。
        TopicAuthorizer authorizer = context.getBean(TopicAuthorizer.class);
        Long conv = conversationService.createGroup(A, Collections.singletonList(B),
                "授权链", null, null, "GROUP").getId();
        String topic = com.zifang.z.msg.api.RealtimeTopics.room(conv);

        assertTrue(authorizer.canSubscribe(A, topic), "成员经真授权链应当放行");
        assertTrue(authorizer.canSubscribe(B, topic));
        assertFalse(authorizer.canSubscribe(OUTSIDER, topic), "非成员经真授权链应当被拒");
        // 对照（关键）：本模块管不着的公共房间依然能订 —— 只要 im 的策略对未知会话返回 FALSE，
        // 这一条就会红，而那正是"把 ws 的默认授权整条链路否掉"。
        assertTrue(authorizer.configuredPublicTopics().contains("room:lobby"), "前置：lobby 配成了公共房间");
        assertTrue(authorizer.canSubscribe(OUTSIDER, "room:lobby"), "公共房间不该被 im 的策略连坐");
        assertFalse(authorizer.canPublish(A, topic), "发言必须走 REST");
    }

    @Test
    public void propertiesAreBoundFromTheZMsgImPrefixWithDocumentedDefaults() {
        ImProperties props = context.getBean(ImProperties.class);
        assertTrue(props.isEnabled(), "默认开启（宿主显式配 false 才关，见 ImDisabledAutoConfigurationTest）");
        assertEquals(50, props.getDefaultPageSize());
        assertEquals(200, props.getMaxPageSize());
        assertEquals(500, props.getMaxMembersPerConversation());
        assertEquals(4000, props.getMaxContentLength());
        assertEquals(200, props.getSeqCasMaxAttempts());
        assertTrue(props.isPublishRealtime());
        assertTrue(props.isPublishReadReceipt());
        assertTrue(props.isUserSidePush());
        assertEquals(200, props.getUserSidePushMaxMembers());
    }

    @Test
    public void fourthEntityIsUsableThroughItsOwnMapper() {
        // ImReadReceiptDO 是本模块新增的第四个实体：能插、能按唯一索引定位到一行
        ImReadReceiptMapper mapper = context.getBean(ImReadReceiptMapper.class);
        Long conv = conversationService.single(A, B, null).getId();
        messageService.send(conv, A, "给回执表用的一条");
        readService.markRead(conv, A, 1L);
        ImReadReceiptDO row = mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query
                .LambdaQueryWrapper<ImReadReceiptDO>()
                .eq(ImReadReceiptDO::getConversationId, conv).eq(ImReadReceiptDO::getUserId, A));
        assertNotNull(row, "回执表里应该有一行");
        assertNotNull(row.getId(), "@TableId(ASSIGN_ID) 要给得出主键");
        assertEquals(1L, row.getLastReadSeq().longValue());
    }

    @Test
    public void restControllersAreExposedUnderTheImPath() {
        // 只证"路由挂上了"：形状与鉴权面在 ImRestApiTest 里逐条打真 HTTP
        assertNotNull(context.getBean(ImConversationController.class));
        assertTrue(context.getBeansWithAnnotation(RestController.class).values().stream()
                .anyMatch(b -> b instanceof ImMessageController), "message controller 应当带 @RestController");
    }
}
