package com.zifang.z.msg.ws.session;

import com.zifang.z.msg.api.RealtimeMessage;
import com.zifang.z.msg.api.RealtimeTopics;
import com.zifang.z.msg.api.RealtimeTransport;
import com.zifang.z.msg.ws.config.WsProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接池 + {@link RealtimeTransport} 实现。
 * <p>
 * 三张索引（按 id / 按 topic / 按用户）都指向同一批 {@link MsgWsSession}，
 * 所以注册与摘除必须是一次原子动作完成：只清一张的话，被摘掉 session 的连接
 * 还会从另一张索引里被投递到（对端已关，白耗一次序列化），更糟的是
 * {@code onlineConnections()} 和实际能收到帧的连接数会长期不一致。
 */
public class WsSessionRegistry implements RealtimeTransport {

    private static final Logger log = LogManager.getLogger(WsSessionRegistry.class);

    private final Map<String, MsgWsSession> byId = new ConcurrentHashMap<String, MsgWsSession>();
    private final Map<String, Set<String>> idsByTopic = new ConcurrentHashMap<String, Set<String>>();
    private final Map<Long, Set<String>> idsByUser = new ConcurrentHashMap<Long, Set<String>>();
    private final WsProperties properties;

    public WsSessionRegistry(WsProperties properties) {
        this.properties = properties;
    }

    /**
     * 登记一条连接，并按 {@code z-msg.ws.max-sessions-per-user} 挤掉最老的那条。
     */
    public MsgWsSession register(MsgWsSession session) {
        byId.put(session.getId(), session);
        indexUser(session, true);
        int limit = properties.getMaxSessionsPerUser();
        if (limit > 0 && session.getUserId() != null) {
            Set<String> ids = idsByUser.get(session.getUserId());
            if (ids != null && ids.size() > limit) {
                List<MsgWsSession> mine = new ArrayList<MsgWsSession>(ids.size());
                for (String id : ids) {
                    MsgWsSession s = byId.get(id);
                    if (s != null) {
                        mine.add(s);
                    }
                }
                // 多出来的数量一次算清：连上 N 台设备又被第 N+1 台挤，只该关最老的
                mine.sort(new java.util.Comparator<MsgWsSession>() {
                    @Override
                    public int compare(MsgWsSession a, MsgWsSession b) {
                        return Long.compare(a.getOpenedAt(), b.getOpenedAt());
                    }
                });
                int toClose = mine.size() - limit;
                for (int i = 0; i < toClose && i < mine.size(); i++) {
                    MsgWsSession oldest = mine.get(i);
                    if (oldest.getId().equals(session.getId())) {
                        continue;
                    }
                    log.info("[z-msg-ws] 超出 max-sessions-per-user={}，挤掉最早连接 id={} userId={}",
                            limit, oldest.getId(), oldest.getUserId());
                    unregister(oldest.getId());
                    oldest.close();
                }
            }
        }
        return session;
    }

    /**
     * 按 id 摘除连接，三张索引一起清。
     *
     * @return 被摘掉的 session；本来就没有则返回 null
     */
    public MsgWsSession unregister(String connectionId) {
        MsgWsSession removed = byId.remove(connectionId);
        if (removed == null) {
            return null;
        }
        for (String topic : removed.topics()) {
            dropFromTopicIndex(topic, connectionId);
        }
        indexUser(removed, false);
        return removed;
    }

    public boolean subscribe(String connectionId, String topic) {
        MsgWsSession s = byId.get(connectionId);
        if (s == null || topic == null || topic.isEmpty()) {
            return false;
        }
        if (!s.subscribe(topic)) {
            return true;
        }
        // add 必须放进 compute 里：computeIfAbsent(topic).add(id) 的那次 add 不在 map 的桶锁内，
        // 于是可以和下面 dropFromTopicIndex 的"发现集合空了就摘掉 key"交错 —— 结果是这条连接在
        // session.topics() 里看着订上了、索引里却没有，从此收不到该 topic 的任何帧。
        idsByTopic.compute(topic, (k, v) -> {
            Set<String> ids = v == null ? ConcurrentHashMap.newKeySet() : v;
            ids.add(connectionId);
            return ids;
        });
        return true;
    }

    public boolean unsubscribe(String connectionId, String topic) {
        MsgWsSession s = byId.get(connectionId);
        if (s == null || !s.unsubscribe(topic)) {
            return false;
        }
        dropFromTopicIndex(topic, connectionId);
        return true;
    }

    /**
     * 从某 topic 的订阅索引里摘掉一条连接，集合空了就把 key 一起回收。
     * <p>
     * "删成员"和"判空后摘 key"必须在同一次 {@code compute} 里做完。分成
     * {@code get → remove → isEmpty → remove(key, value)} 四步的话，摘 key 那一刻
     * 集合可能刚被一个并发 {@link #subscribe} 重新填上（它拿的是同一个集合对象），
     * key 一摘，那条订阅就成了孤儿。断开连接是这条路径最常走的地方：一个人关标签页，
     * 正好另一个人订上同一个房间。
     */
    private void dropFromTopicIndex(String topic, String connectionId) {
        idsByTopic.compute(topic, (k, v) -> {
            if (v == null) {
                return null;
            }
            v.remove(connectionId);
            // 空 topic 不回收的话，一个建过又解散的群会永远占着一个 key
            return v.isEmpty() ? null : v;
        });
    }

    public MsgWsSession find(String connectionId) {
        return byId.get(connectionId);
    }

    public Set<String> topicsOf(String connectionId) {
        MsgWsSession s = byId.get(connectionId);
        return s == null ? java.util.Collections.<String>emptySet() : s.topics();
    }

    @Override
    public int deliverToTopic(String topic, RealtimeMessage message) {
        if (topic == null || message == null) {
            return 0;
        }
        Set<String> ids = idsByTopic.get(topic);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 快照再遍历：投递过程中订阅会变更，直接迭代活视图可能 ConcurrentModification
        Collection<String> snapshot = new ArrayList<String>(ids);
        int sent = 0;
        for (String id : snapshot) {
            MsgWsSession s = byId.get(id);
            if (s == null) {
                continue;
            }
            if (s.send(message)) {
                sent++;
            } else {
                // 写不动就是已经断了，顺手摘掉，别让它留在索引里被反复命中
                unregister(id);
            }
        }
        return sent;
    }

    /**
     * 用户维度投递。
     * <p>
     * 这里必须同时命中"握手时自动订阅 {@code user:<id>}"和"客户端自己订阅了别的 user: topic"
     * 两种情况，所以先按用户索引打一遍，再补一次 topic 索引，最后合并去重——
     * 只用其中一张都会漏连接。
     */
    @Override
    public int deliverToUser(String userId, RealtimeMessage message) {
        if (userId == null || message == null) {
            return 0;
        }
        Set<String> targets = new LinkedHashSet<String>();
        Long asLong = parseUserId(userId);
        if (asLong != null) {
            Set<String> ids = idsByUser.get(asLong);
            if (ids != null) {
                targets.addAll(ids);
            }
        }
        targets.addAll(subscribersOf(RealtimeTopics.user(userId)));
        if (targets.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (String id : targets) {
            MsgWsSession s = byId.get(id);
            if (s == null) {
                continue;
            }
            if (s.send(message)) {
                sent++;
            } else {
                unregister(id);
            }
        }
        return sent;
    }

    /**
     * 某 topic 当前的订阅连接数（诊断与"有没有人在线"判断用）
     */
    public int subscriberCount(String topic) {
        Set<String> ids = idsByTopic.get(topic);
        return ids == null ? 0 : ids.size();
    }

    /**
     * 在线连接 id 快照
     */
    public Set<String> onlineIds() {
        return new LinkedHashSet<String>(byId.keySet());
    }

    @Override
    public int onlineConnections() {
        return byId.size();
    }

    @Override
    public int onlineUsers() {
        int n = 0;
        for (Map.Entry<Long, Set<String>> e : idsByUser.entrySet()) {
            if (!e.getValue().isEmpty()) {
                n++;
            }
        }
        return n;
    }

    @Override
    public String toString() {
        return "WsSessionRegistry{connections=" + byId.size() + ", users=" + idsByUser.size()
                + ", topics=" + idsByTopic.size() + '}';
    }

    private Set<String> subscribersOf(String topic) {
        Set<String> ids = idsByTopic.get(topic);
        return ids == null ? java.util.Collections.<String>emptySet() : new LinkedHashSet<String>(ids);
    }

    private static Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void indexUser(MsgWsSession session, boolean add) {
        Long uid = session.getUserId();
        if (uid == null) {
            return;
        }
        // 和 subscribe 同一个理由：改动要落在 compute 里，才不会和"发现空了就摘 key"交错，
        // 让一条连接从 idsByUser 里漏掉 —— 漏掉的那条不会被挤占判定数到，
        // max-sessions-per-user 就只是个装饰。
        if (add) {
            idsByUser.compute(uid, (k, v) -> {
                Set<String> ids = v == null ? ConcurrentHashMap.newKeySet() : v;
                ids.add(session.getId());
                return ids;
            });
            return;
        }
        idsByUser.compute(uid, (k, v) -> {
            if (v == null) {
                return null;
            }
            v.remove(session.getId());
            return v.isEmpty() ? null : v;
        });
    }
}
