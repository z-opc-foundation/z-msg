package com.zifang.z.msg.ws.session;

import com.zifang.z.msg.ws.config.WsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * topic 索引的并发不变量：一条连接"自认为订上了"，索引里就必须真的有它。
 * <p>
 * 盯的是这一个具体的坑：索引这边会在集合空了之后把 key 摘掉（不摘的话 topic 索引
 * 随聊天室数量单向增长），而被改掉之前的 {@code subscribe} 写的是
 * {@code computeIfAbsent(topic).add(id)} —— 那次 {@code add} 不在 map 的桶锁里。
 * 两者交错时新 add 落进"已经被摘掉的那个集合"，于是这条连接在 {@code session.topics()}
 * 里仍认为自己订着，索引里却没有：从此收不到该 topic 的任何一帧，而且没有任何日志
 * 会说它订过。这里把它钉成一条能跑的判据：<b>自认订上的连接数 == 索引里的连接数</b>。
 * <p>
 * 对撞形状：每一代先让持有者线程把 topic 订上，然后同一道栅栏放开两拨线程 ——
 * 一拨整体断开（最后断开的那次会真的把集合清空并摘掉 key），
 * 另一拨同时订上并且<b>永不退订</b>，被吃掉的那条订阅只能在最后计数时露出来。
 * 一代一个独立 topic，这样每代都会重演一次"集合归零"，而不是订满之后再也不空。
 */
public class WsSessionRegistryIndexTest {

    // 一代 12 个线程全挤在同一个 topic 的同一个桶上：8 个退订里"最后出去的那一个"
    // 才是摘 key 的那一次，它落在什么时刻由调度决定，而 4 个订阅正好可能在它之后才把
    // id 交进集合 —— 两件事重叠的那几纳秒就是坑本身，只能靠次数把它摊开：
    // 2 万次 × 4 = 8 万次订阅，退订 8 万次。
    // 实测（Apple 10 核，mvn -o -pl z-msg-ws test -Dtest=本类）：
    // 把 subscribe 改回 computeIfAbsent(topic).add(id) ⇒ 5 跑 5 红，每次丢 1—8 条订阅；
    // 现在的实现 ⇒ 连续 5 跑零红，一趟 1.5—3.5s。
    // 绿不是"竞态不存在"的证明，只是"这次没有连接被吃掉"的下限保证；
    // 但这道形状一旦退回非原子的 get-then-add，几乎必红，所以别把这条尺删掉。
    private static final int GENERATIONS = 20_000;
    private static final int HOLDERS = 8;
    private static final int JOINERS = 4;

    /**
     * 直接看注册表内部的 topic map：{@code subscriberCount} 为 0 有两种可能
     * （集合还在只是空了 / key 真被摘了），只有后者才会和并发的 subscribe 撞出丢订阅。
     */
    private static Map<String, Object> topicIndex(WsSessionRegistry registry) throws Exception {
        Field f = WsSessionRegistry.class.getDeclaredField("idsByTopic");
        f.setAccessible(true);
        return (Map<String, Object>) f.get(registry);
    }

    @Test
    public void subscriptionSurvivingAnEmptyingRaceIsStillIndexed() throws Exception {
        WsProperties props = new WsProperties();
        // 关掉"同一用户最多几条连接"：本用例要的是 topic 索引，一旦触发挤号，
        // unregister 会连带清掉别人的订阅，判据里就掺进了别的变量
        props.setMaxSessionsPerUser(0);
        final WsSessionRegistry registry = new WsSessionRegistry(props);
        final AtomicInteger claimed = new AtomicInteger();
        final AtomicReference<String> workerFailure = new AtomicReference<String>();

        emptyTopicKeyIsReclaimed(registry);

        final CyclicBarrier subscribed = new CyclicBarrier(HOLDERS + JOINERS);
        final CyclicBarrier roundDone = new CyclicBarrier(HOLDERS + JOINERS);
        Thread[] workers = new Thread[HOLDERS + JOINERS];
        for (int w = 0; w < workers.length; w++) {
            final int worker = w;
            final boolean holder = w < HOLDERS;
            workers[w] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int g = 0; g < GENERATIONS; g++) {
                            String topic = "room:race-" + g;
                            String id = "w" + worker + "-g" + g;
                            MsgWsSession session = new MsgWsSession(id, Long.valueOf(900_000L + g),
                                    fakeRaw(id));
                            if (holder) {
                                registry.register(session);
                                registry.subscribe(id, topic);
                            }
                            subscribed.await();
                            if (holder) {
                                registry.unregister(id);
                            } else {
                                registry.register(session);
                                registry.subscribe(id, topic);
                                if (session.subscribes(topic)) {
                                    claimed.incrementAndGet();
                                }
                            }
                            roundDone.await();
                        }
                    } catch (Throwable t) {
                        workerFailure.compareAndSet(null, String.valueOf(t));
                    }
                }
            }, holder ? "holder-" + w : "joiner-" + w);
            workers[w].setDaemon(true);
            workers[w].start();
        }
        long deadline = System.currentTimeMillis() + 120_000L;
        for (Thread t : workers) {
            t.join(Math.max(1L, deadline - System.currentTimeMillis()));
        }
        for (Thread t : workers) {
            assertFalse(t.isAlive(), "对撞线程没跑完");
        }
        if (workerFailure.get() != null) {
            fail("对撞线程抛了: " + workerFailure.get());
        }
        // 先确认对撞真的跑满了：joiner 少订上一批，后面的"0 == 0"就是假的绿
        assertEquals(GENERATIONS * JOINERS, claimed.get(), "有 joiner 没订上，对撞没跑满");

        int indexed = 0;
        StringBuilder losses = new StringBuilder();
        for (int g = 0; g < GENERATIONS; g++) {
            String topic = "room:race-" + g;
            int n = registry.subscriberCount(topic);
            if (n != JOINERS) {
                if (losses.length() < 400) {
                    losses.append(topic).append('=').append(n).append(' ');
                }
            }
            indexed += n;
        }
        assertEquals(claimed.get(), indexed,
                "有连接自认为订上了却不在索引里（那次订阅落进了已被摘掉的集合），"
                        + "它从此收不到该 topic 的任何一帧。各代实际索引数: " + losses);
    }

    /**
     * 单独钉一次"空 topic 要回收 key"：既是这条行为自己的契约，也是上面那场对撞的猎物来源。
     */
    private void emptyTopicKeyIsReclaimed(WsSessionRegistry registry) throws Exception {
        String topic = "room:gc-check";
        String a = "gc-a";
        String b = "gc-b";
        registry.register(new MsgWsSession(a, Long.valueOf(1L), fakeRaw(a)));
        registry.register(new MsgWsSession(b, Long.valueOf(2L), fakeRaw(b)));
        registry.subscribe(a, topic);
        registry.subscribe(b, topic);
        assertEquals(2, registry.subscriberCount(topic), "订阅没进索引");
        registry.unsubscribe(a, topic);
        registry.unregister(b);
        assertEquals(0, registry.subscriberCount(topic), "退订没清干净");
        assertTrue(!topicIndex(registry).containsKey(topic),
                "空 topic 的 key 没被回收：既会随聊天室数量单向增长，也会让上面的对撞失去猎物");
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
                    return "fake-race:" + id;
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
