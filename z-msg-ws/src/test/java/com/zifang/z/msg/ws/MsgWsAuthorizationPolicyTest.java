package com.zifang.z.msg.ws;

import com.zifang.z.msg.api.RealtimeTopics;
import com.zifang.z.msg.api.TopicAuthorizationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code TopicAuthorizationPolicy} 的三态语义：这是 {@code z-msg-im}（以及任何要开群聊的宿主）
 * 接入实时层的唯一扩展点，所以它必须被端到端验过，而不是只在 {@code TopicAuthorizer} 的单测里自证。
 * <p>
 * 本类同时钉住 {@code op=publish} 不是空实现：默认全拒（见 {@link MsgWsEndToEndTest}）之后，
 * 如果这里不能让一条策略把它放开，那"默认拒绝"就只是因为功能根本没接上。
 */
@SpringBootTest(classes = MsgWsAuthorizationPolicyTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "z-msg.enabled=true",
        "z-msg.web.trusted-header-enabled=true",
        "z-msg.realtime.ticket-secret=e2e-ws-policy-secret-0123456789",
        "z-msg.ws.public-topics[0]=room:lobby",
        "z-msg.ws.max-topics-per-connection=4"
})
public class MsgWsAuthorizationPolicyTest extends WsHarness {

    /** 只被策略放行、不在 public-topics 里的房间：只能靠 {@link RoomPolicy} 订上 */
    private static final String ROOM = "room:7001";

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean(name = "dataSourceMsg")
        public DataSource dataSourceMsg() {
            return h2("msg_ws_policy");
        }

        /** 群聊模块的形状：管 room: 前缀，读写都放行。 */
        @Bean
        public TopicAuthorizationPolicy roomPolicy() {
            return new RoomPolicy();
        }

        /** 业务事件模块的形状：管 biz: 前缀，只放行订阅。 */
        @Bean
        public TopicAuthorizationPolicy bizPolicy() {
            return new BizPolicy();
        }

        /** 后到的一条否决策略：证明 FALSE 优先于先前的 TRUE。 */
        @Bean
        public TopicAuthorizationPolicy bizVetoPolicy() {
            return new BizVetoPolicy();
        }
    }

    public static class RoomPolicy implements TopicAuthorizationPolicy {
        @Override
        public boolean supports(String topic) {
            return RealtimeTopics.isRoom(topic);
        }

        @Override
        public Boolean allowSubscribe(Long userId, String topic) {
            return Boolean.TRUE;
        }

        @Override
        public Boolean allowPublish(Long userId, String topic) {
            // 真群聊要按成员表判；这里只需要"策略能放开写侧"这条语义成立
            return Boolean.TRUE;
        }

        @Override
        public int order() {
            return 10;
        }
    }

    public static class BizPolicy implements TopicAuthorizationPolicy {
        @Override
        public boolean supports(String topic) {
            return topic != null && topic.startsWith(RealtimeTopics.P_BIZ);
        }

        @Override
        public Boolean allowSubscribe(Long userId, String topic) {
            return Boolean.TRUE;
        }

        @Override
        public int order() {
            return 20;
        }
    }

    public static class BizVetoPolicy implements TopicAuthorizationPolicy {
        @Override
        public boolean supports(String topic) {
            return "biz:veto".equals(topic);
        }

        @Override
        public Boolean allowSubscribe(Long userId, String topic) {
            return Boolean.FALSE;
        }

        @Override
        public int order() {
            return 30;
        }
    }

    // ---------------- 策略放行后，读与写两条路都要真的通 ----------------

    @Test
    public void policyTrueOpensSubscribeOnItsOwnPrefix() {
        Recorder a = connect(USER_A);
        awaitReady(a);
        assertEquals(3, registry.topicsOf(connectionIdOf(a)).size(),
                "前置：默认只自动绑 user:<自己>/sys:broadcast/room:lobby");

        assertVerbContains(subscribeAndWait(a, "p-room", ROOM), "subscribed", ROOM);
        assertTrue(registry.topicsOf(connectionIdOf(a)).contains(ROOM),
                "策略放行的 topic 要真的进订阅集");

        // 对照：策略只管自己 supports 的前缀，tenant: 没人认领就该照旧拒
        subscribe(a, "p-tenant", "tenant:acme");
        assertEquals("WS_TOPIC_FORBIDDEN", awaitErrorCode(a, "WS_TOPIC_FORBIDDEN").get("errorCode"));
    }

    @Test
    public void policyTrueOpensTheClientPublishPath() {
        Recorder a = connect(USER_A);
        Recorder b = connect(USER_B);
        awaitReady(a);
        awaitReady(b);
        assertVerbContains(subscribeAndWait(b, "b-room", ROOM), "subscribed", ROOM);

        send(a, "{\"op\":\"publish\",\"clientMsgId\":\"pub-1\",\"topic\":\"" + ROOM
                + "\",\"kind\":\"chat\",\"payload\":{\"text\":\"策略放行后的代发\"}}");
        Map<String, Object> ack = awaitAckRaw(a, "pub-1");
        Map<String, Object> info = payloadOf(ack);
        assertEquals(ROOM, info.get("topic"));
        assertEquals(1, ((Number) info.get("delivered")).longValue(),
                "delivered 要如实报命中连接数，实际: " + info);
        awaitFrame(b, withText("策略放行后的代发"), "b 的代发帧");
        assertFalse(containsText(a, "策略放行后的代发"),
                "代发只给订阅者，不自动回显给发送方（发送方自己本地插气泡）: " + a.dump());

        // 对照：写侧同样按前缀划界，没策略认领的 user: 照旧拒
        send(a, "{\"op\":\"publish\",\"clientMsgId\":\"pub-2\",\"topic\":\""
                + RealtimeTopics.user(USER_B) + "\",\"payload\":{\"text\":\"越权写\"}}");
        Map<String, Object> denied = awaitErrorCode(a, "WS_TOPIC_FORBIDDEN");
        assertEquals("pub-2", denied.get("clientMsgId"), "实际: " + denied);
    }

    // ---------------- FALSE 优先：一条策略就能盖掉另一条的放行 ----------------

    @Test
    public void falseVoteOutweighsAnEarlierTrueVote() {
        Recorder a = connect(USER_A);
        awaitReady(a);

        assertVerbContains(subscribeAndWait(a, "ok-biz", "biz:order"), "subscribed", "biz:order");
        subscribe(a, "no-biz", "biz:veto");
        Map<String, Object> err = awaitErrorCode(a, "WS_TOPIC_FORBIDDEN");
        assertEquals("no-biz", err.get("clientMsgId"), "实际: " + err);
        assertFalse(registry.topicsOf(connectionIdOf(a)).contains("biz:veto"),
                "被否决的 topic 不能进订阅集");
    }

    // ---------------- 订阅上限：越界的一帧整帧不改状态 ----------------

    @Test
    public void topicCapRejectsTheWholeFrameAndLeavesSubscriptionsUntouched() {
        Recorder a = connect(USER_A);
        awaitReady(a);
        String id = connectionIdOf(a);
        assertEquals(4, wsProperties.getMaxTopicsPerConnection(), "前置：上限按 4 配置");
        assertEquals(3, registry.topicsOf(id).size(), "前置：自动订阅三条");

        assertVerbContains(subscribeAndWait(a, "one", "room:1"), "subscribed", "room:1");
        assertEquals(4, registry.topicsOf(id).size(), "补到上限时应当正好 4 条");

        subscribe(a, "two", "room:2", "room:3");
        Map<String, Object> err = awaitErrorCode(a, "WS_TOPIC_LIMIT");
        assertEquals("two", err.get("clientMsgId"), "实际: " + err);
        assertEquals(4, registry.topicsOf(id).size(),
                "越界的一帧要整帧不改状态，不能只订进去一半: " + registry.topicsOf(id));
        assertFalse(registry.topicsOf(id).contains("room:2"), "实际: " + registry.topicsOf(id));
    }
}
