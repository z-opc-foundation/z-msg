package com.zifang.z.msg.ws.session;

import com.zifang.z.msg.api.RealtimeMessage;
import com.zifang.z.msg.api.RealtimeTopics;
import com.zifang.z.msg.ws.config.WsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WsSessionRegistry#rebindIdentity} 的索引不变量：换身份之后，
 * 按用户的索引必须跟着搬走，而按 topic 的索引一条都不许丢。
 * <p>
 * 为什么单独立一类而不是塞进 E2E：换身份这件事最坏的失败形状是"连接还在线、
 * 界面上一切正常，但红点永远不来"——它在 HTTP/帧层面看着都是成功的，只有直接对两张索引
 * 读数才看得见。E2E 里另有对应的端到端一例（{@code MsgWsInbandAuthTest}）兜住"真的收到帧"。
 * <p>
 * 两层不是重复：把 {@code rebindIdentity} 里"顺手摘掉旧 {@code user:<id>} topic"那一行删掉，
 * <b>只有本类会红</b>（E2E 那一例被 handler 换身份后的复核层兜住了）。而宿主可以绕开 handler
 * 直接注入注册表改身份，那一层就没有第二道保险。这条事实是实测出来的，不是推测。
 */
public class WsSessionRegistryRebindTest {

    private static final long OLD_USER = 8101L;
    private static final long NEW_USER = 8102L;

    @Test
    public void rebindMovesTheUserIndexAndKeepsEveryTopicSubscription() throws Exception {
        WsProperties props = new WsProperties();
        props.setMaxSessionsPerUser(0);
        WsSessionRegistry registry = new WsSessionRegistry(props);

        String room = "room:rebind";
        MsgWsSession s = new MsgWsSession("c1", Long.valueOf(OLD_USER), fakeRaw("c1"));
        registry.register(s);
        registry.subscribe("c1", RealtimeTopics.user(OLD_USER));
        registry.subscribe("c1", room);

        // 先立猎物：改之前两张索引都数得到，否则下面的"变成 0"只是因为压根没接上
        assertEquals(1, registry.deliverToUser(String.valueOf(OLD_USER), frame()),
                "改之前旧身份必须收得到（猎物）");
        assertEquals(0, registry.deliverToUser(String.valueOf(NEW_USER), frame()),
                "改之前新身份不该收得到");
        assertEquals(1, registry.subscriberCount(room), "房间订阅没进 topic 索引（猎物）");

        Long previous = registry.rebindIdentity("c1", Long.valueOf(NEW_USER));
        assertEquals(Long.valueOf(OLD_USER), previous);
        assertEquals(Long.valueOf(NEW_USER), s.getUserId().longValue());

        // 旧身份必须彻底断掉：还留在 idsByUser 里的话，别人给 OLD_USER 发消息会投进这条连接，
        // 而它现在自称是 NEW_USER —— 越权读别人的红点，且没有任何一帧会报错
        assertEquals(0, registry.deliverToUser(String.valueOf(OLD_USER), frame()),
                "换身份后旧用户的投递还命中着这条连接（idsByUser 没搬干净）");
        assertEquals(1, registry.deliverToUser(String.valueOf(NEW_USER), frame()),
                "换身份后新用户投不到这条连接");
        assertEquals(1, registry.onlineUsers(), "按用户在线数还停在旧索引上");
        assertTrue(topicIndex(registry).containsKey(room), "换身份把房间订阅弄丢了");
        assertEquals(1, registry.subscriberCount(room), "换身份后房间订阅数漂了");
        // deliverToUser 是"按用户索引 + 按 user:<id> 的 topic 索引"两路合并，所以光搬
        // idsByUser 而留着 user:<旧id> 这条订阅，旧用户照样能投进这条连接 —— 上面那句
        // "旧身份投递命中 0"只有两条一起断才成立，这里把第二条也钉住。
        assertFalse(s.subscribes(RealtimeTopics.user(OLD_USER)),
                "换身份后还订阅着旧用户的 topic");
        assertTrue(s.subscribes(room), "与身份无关的房间订阅必须原样留着");
    }

    @Test
    public void rebindToTheSameIdentityChangesNothing() {
        WsProperties props = new WsProperties();
        props.setMaxSessionsPerUser(0);
        WsSessionRegistry registry = new WsSessionRegistry(props);
        MsgWsSession s = new MsgWsSession("c2", Long.valueOf(OLD_USER), fakeRaw("c2"));
        registry.register(s);
        registry.subscribe("c2", RealtimeTopics.user(OLD_USER));

        assertEquals(Long.valueOf(OLD_USER), registry.rebindIdentity("c2", Long.valueOf(OLD_USER)));
        assertEquals(Long.valueOf(OLD_USER), s.getUserId().longValue());
        assertEquals(1, registry.deliverToUser(String.valueOf(OLD_USER), frame()),
                "同身份续期不该动索引");
    }

    @Test
    public void rebindIntoACrowdedUserEvictsTheOldestConnection() {
        WsProperties props = new WsProperties();
        props.setMaxSessionsPerUser(1);
        WsSessionRegistry registry = new WsSessionRegistry(props);

        MsgWsSession oldA = new MsgWsSession("a-old", Long.valueOf(OLD_USER), fakeRaw("a-old"));
        registry.register(oldA);
        // 挤在 openedAt 之外没有别的排序依据，所以这里靠"注册顺序"就够了：
        // 两条连接的 openedAt 可能同毫秒，比较器对相等值不区分先后 —— 但被挤的一定是已存在的那条，
        // 因为 rebind 进来的这条是"最新的"，enforceSessionLimit 会跳过自己。
        MsgWsSession incoming = new MsgWsSession("b-1", Long.valueOf(NEW_USER), fakeRaw("b-1"));
        registry.register(incoming);

        assertEquals(2, registry.onlineConnections());
        registry.rebindIdentity("b-1", Long.valueOf(OLD_USER));

        assertEquals(1, registry.onlineConnections(),
                "换身份后落进同一名下已超过 max-sessions-per-user=1，最老那条必须被挤掉");
        assertNull(registry.find("a-old"), "被挤掉的应当是已存在的那条旧连接");
        assertEquals(Long.valueOf(OLD_USER), incoming.getUserId().longValue());
        assertEquals(1, registry.deliverToUser(String.valueOf(OLD_USER), frame()),
                "活下来的那条必须还能收到自己用户的帧");
    }

    @Test
    public void rebindOfAnUnregisteredConnectionIsANoOp() {
        WsSessionRegistry registry = new WsSessionRegistry(new WsProperties());
        assertNull(registry.rebindIdentity("ghost", Long.valueOf(NEW_USER)),
                "不在注册表里的连接不该被凭空记账");
        assertEquals(0, registry.onlineConnections());
    }

    private static RealtimeMessage frame() {
        return RealtimeMessage.frame(RealtimeMessage.OP_MESSAGE);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> topicIndex(WsSessionRegistry registry) throws Exception {
        Field f = WsSessionRegistry.class.getDeclaredField("idsByTopic");
        f.setAccessible(true);
        return (Map<String, Object>) f.get(registry);
    }

    private static WebSocketSession fakeRaw(final String id) {
        final Map<String, Object> attributes = new ConcurrentHashMap<String, Object>();
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if ("isOpen".equals(name)) {
                    return Boolean.TRUE;
                }
                if ("getId".equals(name)) {
                    return id;
                }
                if ("getAttributes".equals(name)) {
                    return attributes;
                }
                if ("hashCode".equals(name)) {
                    return Integer.valueOf(System.identityHashCode(proxy));
                }
                if ("equals".equals(name)) {
                    return Boolean.valueOf(proxy == args[0]);
                }
                if ("toString".equals(name)) {
                    return "fake-rebind:" + id;
                }
                Class<?> rt = method.getReturnType();
                if (rt == boolean.class) {
                    return Boolean.FALSE;
                }
                if (rt == int.class) {
                    return Integer.valueOf(0);
                }
                if (rt == long.class) {
                    return Long.valueOf(0L);
                }
                return null;
            }
        };
        return (WebSocketSession) Proxy.newProxyInstance(WebSocketSession.class.getClassLoader(),
                new Class<?>[]{WebSocketSession.class}, handler);
    }
}
