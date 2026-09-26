package com.zifang.z.msg.im.auth;

import com.zifang.z.msg.api.RealtimeTopics;
import com.zifang.z.msg.im.ImSpringTestSupport;
import com.zifang.z.msg.im.domain.model.ImConvTypes;
import org.junit.jupiter.api.Test;

import javax.annotation.Resource;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ImTopicAuthorizationPolicy} 的三态语义。
 * <p>
 * 这个类最要紧的不是"成员放行、外人拒绝"，而是<strong>什么时候必须闭嘴（返回 null）</strong>：
 * {@code TopicAuthorizer} 的规则是"任一策略给 FALSE 就立即否决"，所以对本模块管不着的
 * topic（{@code room:lobby} 这种 key 不是会话 id 的、以及 id 压根不在本模块会话表里的）
 * 一旦返回 FALSE，就会把宿主在 {@code z-msg.ws.public-topics} 里显式声明的公共房间、
 * 甚至别的模块的 room 实现整条锁死——本模块越权替别人做了决定。
 * <p>
 * 因此下面每支"null"都配了"我自家的 room 要真表过态"的对照：
 * 否则"策略从来没被问到"也能让这类断言全绿。
 */
public class ImTopicAuthorizationPolicyTest extends ImSpringTestSupport {

    @Resource
    private ImTopicAuthorizationPolicy policy;

    @Test
    public void membersAreAllowedAndStrangersRefusedOnTheirOwnRoom() {
        Long conv = conversationService.createGroup(A, Arrays.asList(B, OUTSIDER),
                "策略：自家房间", null, null, ImConvTypes.GROUP).getId();
        String topic = RealtimeTopics.room(conv);

        assertEquals(Boolean.TRUE, policy.allowSubscribe(A, topic), "成员必须放行");
        assertEquals(Boolean.TRUE, policy.allowSubscribe(B, topic));
        assertEquals(Boolean.FALSE, policy.allowSubscribe(OUTSIDER_2, topic),
                "非成员必须拒绝——否则任何人都能旁听别人的会话");

        // 对照：同一条策略在自家会话上表过态了，所以上面那个 FALSE 不是"永远 FALSE"
        assertTrue(conversationService.isMember(conv, OUTSIDER));
        assertEquals(Boolean.TRUE, policy.allowSubscribe(OUTSIDER, topic));

        // 退群之后立刻不再放行（订阅授权读的是成员表的实时状态，不是连接建立时的快照）
        conversationService.leave(OUTSIDER, conv);
        assertEquals(Boolean.FALSE, policy.allowSubscribe(OUTSIDER, topic));
    }

    @Test
    public void anonymousConnectionGetsNoFreePassOnRooms() {
        Long conv = conversationService.single(A, B, null).getId();
        assertEquals(Boolean.FALSE, policy.allowSubscribe(null, RealtimeTopics.room(conv)),
                "没有身份的连接不能订阅别人的会话");
        // 对照：有身份就通
        assertEquals(Boolean.TRUE, policy.allowSubscribe(A, RealtimeTopics.room(conv)));
    }

    @Test
    public void roomsThisModuleDoesNotOwnAreLeftToTheOtherPolicies() {
        // 非数字 key：可能是宿主自己实现的 lobby/公告房
        assertNull(policy.allowSubscribe(A, "room:lobby"), "room:lobby 不归本模块管，必须 null");
        assertNull(policy.allowSubscribe(A, "room:"), "空 key 也必须 null");
        assertNull(policy.allowSubscribe(A, "room:123abc"));
        // 数字但本模块表里没有：不能替别人判死刑
        assertNull(policy.allowSubscribe(A, "room:987654321987"),
                "会话不存在时返回 null，FALSE 会把 ws 的默认授权整条链路否掉");

        // 对照：把那个 id 真的建成会话，同一条 topic 立刻从 null 变成明确表态
        Long conv = conversationService.createGroup(A, java.util.Collections.singletonList(B),
                "存在性对照", null, null, ImConvTypes.ROOM).getId();
        assertNull(policy.allowSubscribe(OUTSIDER, RealtimeTopics.room(999999L)), "前置：未知 id 是 null");
        assertEquals(Boolean.FALSE, policy.allowSubscribe(OUTSIDER, RealtimeTopics.room(conv)),
                "已知 id + 非成员 = FALSE，这才证明上面那个 null 是判过存在的");
        assertEquals(Boolean.TRUE, policy.allowSubscribe(B, RealtimeTopics.room(conv)),
                "ROOM 类型的会话同样按成员判");
    }

    @Test
    public void otherPrefixesAreNotThisPolicySProblem() {
        assertNull(policy.allowSubscribe(A, RealtimeTopics.user(A)), "user: 由 ws 的默认策略管");
        assertNull(policy.allowSubscribe(A, RealtimeTopics.user(B)));
        assertNull(policy.allowSubscribe(A, RealtimeTopics.tenant("acme")));
        assertNull(policy.allowSubscribe(A, "biz:order:1"));
        assertNull(policy.allowSubscribe(A, "sys:broadcast"));
        assertNull(policy.allowSubscribe(A, null), "null topic 不能抛 NPE 把握手打断");
        // 对照：同一次调用里换成 room: 就要表态
        Long conv = conversationService.single(A, B, null).getId();
        assertEquals(Boolean.TRUE, policy.allowSubscribe(A, RealtimeTopics.room(conv)));
    }

    @Test
    public void clientPublishIntoRoomsIsDeniedWhileOtherPublishesAreNotThisModulesCall() {
        Long conv = conversationService.single(A, B, null).getId();
        // 成员也不行：发言必须走 REST（要落库、要占 seq），否则客户端能往房间里塞不落库的假消息，
        // 另一端按 seq 增量补拉时对不上账。
        assertEquals(Boolean.FALSE, policy.allowPublish(A, RealtimeTopics.room(conv)));
        assertEquals(Boolean.FALSE, policy.allowPublish(OUTSIDER, RealtimeTopics.room(conv)));
        assertEquals(Boolean.FALSE, policy.allowPublish(null, RealtimeTopics.room(conv)));
        assertNull(policy.allowPublish(A, RealtimeTopics.user(A)));
        assertNull(policy.allowPublish(A, "room:lobby"));

        // 对照：订阅面在同一条 topic 上是放行的 —— 拒的是 publish，不是这条 room
        assertEquals(Boolean.TRUE, policy.allowSubscribe(A, RealtimeTopics.room(conv)));
    }

    @Test
    public void supportsAndOrderMatchTheContract() {
        assertTrue(policy.supports("room:1"));
        assertFalse(policy.supports("user:1"));
        assertFalse(policy.supports("tenant:acme"));
        assertFalse(policy.supports(null));
        // 比默认策略（order=100）先问：本模块的策略更具体
        assertTrue(policy.order() < 100, "order 要在默认策略之前，实际 " + policy.order());
    }

    @Test
    public void topicKeyParsingHandlesTheEdgesWithoutThrowing() {
        assertNull(ImTopicAuthorizationPolicy.conversationIdOf("user:1"));
        assertNull(ImTopicAuthorizationPolicy.conversationIdOf("room:"));
        assertNull(ImTopicAuthorizationPolicy.conversationIdOf("room:abc"));
        assertNull(ImTopicAuthorizationPolicy.conversationIdOf("room:9223372036854775808"),
                "超 Long 的 key 要当成\"不是我的房间\"，而不是把 NumberFormatException 抛进订阅链路");
        assertEquals(Long.valueOf(123L), ImTopicAuthorizationPolicy.conversationIdOf("room:123"));
        assertEquals(Long.valueOf(456L), ImTopicAuthorizationPolicy.conversationIdOf("room: 456 "),
                "前后空白按 ws 协议的宽松解析处理");
    }
}
