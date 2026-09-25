package com.zifang.z.msg.api;

/**
 * 实时通道传输层 SPI (1.1.0)
 * <p>
 * z-msg-core 只认这个接口；真正的传输由可选模块提供：
 * <ul>
 *   <li>{@code z-msg-ws} — WebSocket / SSE 本机连接池实现</li>
 *   <li>业务侧自行实现 — 例如桥接到 z-team MsgHub 或 Redis pub/sub 做跨节点广播</li>
 * </ul>
 * 没有实现时站内信只落库不实时推，功能不报错。
 */
public interface RealtimeTransport {

    /**
     * 向某个 topic 的在线连接投递，返回命中连接数
     */
    int deliverToTopic(String topic, RealtimeMessage message);

    /**
     * 向某个用户的全部在线连接投递，返回命中连接数
     */
    int deliverToUser(String userId, RealtimeMessage message);

    /**
     * 当前在线连接数
     */
    int onlineConnections();

    /**
     * 在线用户数（同一用户多端算一个）
     */
    int onlineUsers();

    /**
     * 是否支持服务端主动推送（SSE-only 实现可返回 false）
     */
    default boolean pushSupported() {
        return true;
    }
}
