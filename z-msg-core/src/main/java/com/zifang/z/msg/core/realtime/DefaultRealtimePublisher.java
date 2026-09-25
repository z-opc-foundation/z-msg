package com.zifang.z.msg.core.realtime;

import com.zifang.z.msg.api.RealtimeMessage;
import com.zifang.z.msg.api.RealtimePublisher;
import com.zifang.z.msg.api.RealtimeTopics;
import com.zifang.z.msg.api.RealtimeTransport;
import com.zifang.z.msg.core.json.MsgJson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RealtimePublisher 默认实现 (1.1.0)
 * <p>
 * 只做三件事：序列化 payload、给 topic 分配单调 seq、把帧转投给所有 {@link RealtimeTransport}。
 * <p>
 * seq 默认取本机内存计数——单节点内单调即可满足"重连后按 sinceSeq 增量拉"的需求，
 * 因为离线补拉以 DB 里的 seq 为准（见 z-msg-im 的会话 last_seq），这里只是给在线帧编号。
 * 跨节点部署时，接入层若要避免各节点 seq 交叉，应显式传 seq（publish 的四参重载）。
 */
public class DefaultRealtimePublisher implements RealtimePublisher {

    private static final Logger log = LogManager.getLogger(DefaultRealtimePublisher.class);

    private final List<RealtimeTransport> transports;
    private final Map<String, AtomicLong> topicSeq = new ConcurrentHashMap<>();

    public DefaultRealtimePublisher(List<RealtimeTransport> transports) {
        this.transports = transports == null ? Collections.<RealtimeTransport>emptyList() : transports;
    }

    @Override
    public int publish(String topic, String kind, Object payload) {
        return publish(topic, kind, payload, nextSeq(topic));
    }

    @Override
    public int publish(String topic, String kind, Object payload, long seq) {
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        RealtimeMessage frame = RealtimeMessage.message(topic, kind, MsgJson.toJson(payload), seq);
        int hit = 0;
        for (RealtimeTransport t : transports) {
            try {
                if (RealtimeTopics.isUser(topic)) {
                    hit += t.deliverToUser(RealtimeTopics.keyOf(topic), frame);
                } else {
                    hit += t.deliverToTopic(topic, frame);
                }
            } catch (Exception e) {
                // 一个传输实现故障不能带走其他实现（例如 SSE 兜底仍要发出去）
                log.warn("[z-msg] transport {} 投递失败 topic={} err={}",
                        t.getClass().getSimpleName(), topic, e.toString());
            }
        }
        if (hit == 0 && log.isDebugEnabled()) {
            log.debug("[z-msg] 无在线连接命中 topic={} kind={} seq={}", topic, kind, seq);
        }
        return hit;
    }

    @Override
    public int publishToUser(Object userId, String kind, Object payload) {
        if (userId == null) {
            return 0;
        }
        return publish(RealtimeTopics.user(userId), kind, payload);
    }

    @Override
    public long lastSeq(String topic) {
        AtomicLong v = topic == null ? null : topicSeq.get(topic);
        return v == null ? 0L : v.get();
    }

    /**
     * 预置 topic 水位（例如 IM 从 DB 读到 last_seq 后回填，保证在线帧 seq 与库里连续）
     */
    public void primeSeq(String topic, long seq) {
        if (topic == null || topic.isEmpty()) {
            return;
        }
        topicSeq.computeIfAbsent(topic, k -> new AtomicLong()).updateAndGet(cur -> Math.max(cur, seq));
    }

    private long nextSeq(String topic) {
        return topicSeq.computeIfAbsent(topic, k -> new AtomicLong()).incrementAndGet();
    }

    public int transportCount() {
        return transports.size();
    }
}
