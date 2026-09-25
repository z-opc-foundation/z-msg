package com.zifang.z.msg.api;

/**
 * 业务侧唯一需要注入的实时投递入口 (1.1.0)
 * <p>
 * 站内信、IM、业务自定义事件都通过它把消息推给在线连接；实际传输由
 * {@link RealtimeTransport} 实现（z-msg-ws 的 WebSocket/SSE，或业务侧自建的跨节点桥接）。
 * <p>
 * 无 transport 时 publish 返回 0 且不抛异常——实时推送是站内信的增强，不是前提。
 */
public interface RealtimePublisher {

    /**
     * 向 topic 投递，payload 由实现序列化为 JSON。
     *
     * @param topic 见 {@link RealtimeTopics}
     * @param kind  业务语义，见 {@link RealtimeMessage#KIND_CHAT} 等
     * @return 命中的在线连接数（所有 transport 累加）
     */
    int publish(String topic, String kind, Object payload);

    /**
     * 带 topic 内序列号投递（IM 用 seq 做增量同步与去重）
     */
    int publish(String topic, String kind, Object payload, long seq);

    /**
     * 向指定用户投递（自动补 {@code user:} 前缀，已经是 topic 的入参原样使用）
     */
    int publishToUser(Object userId, String kind, Object payload);

    /**
     * 某 topic 服务端最后分配的 seq，无记录返回 0
     */
    long lastSeq(String topic);
}
