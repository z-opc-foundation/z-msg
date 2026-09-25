package com.zifang.z.msg.ws.session;

import com.zifang.z.msg.api.RealtimeMessage;
import com.zifang.z.msg.core.json.MsgJson;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一条 WebSocket 连接的服务端视图。
 * <p>
 * 存在的唯一理由是把三件事钉在同一个对象上：身份（握手时定死，之后不可变）、
 * 订阅集合、以及**发送串行化**。
 * <p>
 * 第三件不是洁癖：{@link WebSocketSession#sendMessage} 明确不是线程安全的，
 * 而这里会有多个业务线程同时往同一条连接写（站内信线程 + IM 线程 + 心跳），
 * 不加锁的表现是偶发的帧内容交错或直接 {@code IllegalStateException}——
 * 只在真并发下才出现，测试跑得少就看不见。
 */
public class MsgWsSession {

    private final String id;
    private final Long userId;
    private final WebSocketSession raw;
    private final long openedAt;
    private final Set<String> topics = ConcurrentHashMap.newKeySet();
    private final Object sendLock = new Object();
    private volatile long lastActiveAt;

    public MsgWsSession(String id, Long userId, WebSocketSession raw) {
        this.id = id;
        this.userId = userId;
        this.raw = raw;
        this.openedAt = System.currentTimeMillis();
        this.lastActiveAt = this.openedAt;
    }

    /**
     * @return true 表示帧已交给容器；false 表示连接已关或写失败（调用方据此清理注册表）
     */
    public boolean send(RealtimeMessage frame) {
        if (frame == null) {
            return false;
        }
        String text = MsgJson.toJson(frame);
        if (text == null) {
            return false;
        }
        synchronized (sendLock) {
            if (!raw.isOpen()) {
                return false;
            }
            try {
                raw.sendMessage(new TextMessage(text));
                lastActiveAt = System.currentTimeMillis();
                return true;
            } catch (IOException | IllegalStateException e) {
                // 对端断了 / 容器已关闭该 session：都按"这条连接没了"处理，
                // 不往上抛，否则一个浏览器关掉标签页就能带走一次业务投递。
                return false;
            }
        }
    }

    public boolean isOpen() {
        return raw.isOpen();
    }

    public void close() {
        try {
            raw.close();
        } catch (Exception ignore) {
            // 已经断了；调用方紧接着就会从注册表里摘掉它
        }
    }

    public boolean subscribe(String topic) {
        return topic != null && !topic.isEmpty() && topics.add(topic);
    }

    public boolean unsubscribe(String topic) {
        return topics.remove(topic);
    }

    public boolean subscribes(String topic) {
        return topics.contains(topic);
    }

    /**
     * 只读视图：外部拿到的订阅集合不能改，否则授权判断会被绕过
     */
    public Set<String> topics() {
        return Collections.unmodifiableSet(topics);
    }

    public String getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public WebSocketSession raw() {
        return raw;
    }

    public long getOpenedAt() {
        return openedAt;
    }

    public long getLastActiveAt() {
        return lastActiveAt;
    }

    public void touch() {
        this.lastActiveAt = System.currentTimeMillis();
    }
}
