package com.zifang.z.msg.ws.handler;

import com.zifang.z.msg.api.RealtimeMessage;
import com.zifang.z.msg.api.RealtimePublisher;
import com.zifang.z.msg.api.RealtimeTopics;
import com.zifang.z.msg.core.json.MsgJson;
import com.zifang.z.msg.ws.auth.TopicAuthorizer;
import com.zifang.z.msg.ws.config.WsProperties;
import com.zifang.z.msg.ws.handshake.MsgHandshakeInterceptor;
import com.zifang.z.msg.ws.session.MsgWsSession;
import com.zifang.z.msg.ws.session.WsSessionRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * z-msg-ws 的帧处理：一条连接同时承载站内信红点、聊天室帧和业务自定义事件。
 * <p>
 * 身份只在握手阶段确定一次（{@code ATTR_USER_ID}），之后每一帧都从 session 属性里取，
 * 绝不从帧内容里读 {@code userId}——否则任何客户端都能在帧里改成别人的 id。
 * <p>
 * 协议见 {@code z-msg/_doc/001_WS_PROTOCOL.md}。
 */
public class MsgWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LogManager.getLogger(MsgWebSocketHandler.class);

    private static final String ERR_BAD_FRAME = "WS_BAD_FRAME";
    private static final String ERR_FORBIDDEN = "WS_TOPIC_FORBIDDEN";
    private static final String ERR_UNSUPPORTED = "WS_OP_UNSUPPORTED";
    private static final String ERR_LIMIT = "WS_TOPIC_LIMIT";

    private final WsSessionRegistry registry;
    private final TopicAuthorizer authorizer;
    private final RealtimePublisher publisher;
    private final WsProperties properties;

    public MsgWebSocketHandler(WsSessionRegistry registry, TopicAuthorizer authorizer,
                               RealtimePublisher publisher, WsProperties properties) {
        this.registry = registry;
        this.authorizer = authorizer;
        this.publisher = publisher;
        this.properties = properties;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) {
        Long userId = (Long) raw.getAttributes().get(MsgHandshakeInterceptor.ATTR_USER_ID);
        String id = (String) raw.getAttributes().get(MsgHandshakeInterceptor.ATTR_CONNECTION_ID);
        if (userId == null || id == null) {
            // 走到这里说明握手拦截器被换掉了或者没生效——不能给它一条匿名连接
            closeQuietly(raw, CloseStatus.POLICY_VIOLATION);
            return;
        }
        MsgWsSession session = new MsgWsSession(id, userId, raw);
        registry.register(session);

        List<String> auto = new ArrayList<String>();
        auto.add(RealtimeTopics.user(userId));
        auto.add(RealtimeTopics.SYS_BROADCAST);
        Collection<String> publicTopics = authorizer.configuredPublicTopics();
        auto.addAll(publicTopics);

        List<String> bound = new ArrayList<String>();
        for (String topic : auto) {
            if (topic == null || topic.isEmpty()) {
                continue;
            }
            if (authorizer.canSubscribe(userId, topic)) {
                registry.subscribe(id, topic);
                bound.add(topic);
            }
        }

        RealtimeMessage ready = RealtimeMessage.frame(RealtimeMessage.OP_READY);
        Map<String, Object> info = new java.util.LinkedHashMap<String, Object>();
        info.put("connectionId", id);
        info.put("userId", userId);
        info.put("topics", bound);
        ready.setPayload(MsgJson.toJson(info));
        if (!session.send(ready)) {
            registry.unregister(id);
        }
        if (log.isInfoEnabled()) {
            log.info("[z-msg-ws] 连接建立 id={} userId={} 自动订阅={} 在线={}",
                    id, userId, bound.size(), registry.onlineConnections());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession raw, TextMessage message) {
        MsgWsSession session = registry.find(connectionId(raw));
        if (session == null) {
            // 连接已从注册表摘掉（对端断开或超出上限被挤掉），不再处理它的帧
            return;
        }
        Long userId = session.getUserId();
        Map<String, Object> frame = MsgJson.toMap(message.getPayload());
        if (frame == null || frame.get("op") == null) {
            session.send(RealtimeMessage.error(ERR_BAD_FRAME, "帧必须是带 op 字段的 JSON 对象"));
            return;
        }
        String op = String.valueOf(frame.get("op"));
        String clientMsgId = str(frame.get("clientMsgId"));

        if (RealtimeMessage.OP_PING.equals(op)) {
            RealtimeMessage pong = RealtimeMessage.frame(RealtimeMessage.OP_PONG);
            pong.setClientMsgId(clientMsgId);
            session.send(pong);
            session.touch();
            return;
        }
        if (RealtimeMessage.OP_SUBSCRIBE.equals(op) || RealtimeMessage.OP_UNSUBSCRIBE.equals(op)) {
            boolean subscribing = RealtimeMessage.OP_SUBSCRIBE.equals(op);
            List<String> topics = readTopics(frame.get("topics"));
            if (topics.isEmpty()) {
                RealtimeMessage err = RealtimeMessage.error(ERR_BAD_FRAME,
                        "topics 不能为空，形如 {\"op\":\"subscribe\",\"topics\":[\"room:lobby\"]}");
                // 每一条回给客户端的帧都要带 clientMsgId：前端靠它对号，缺了就永远卡在"发送中"
                err.setClientMsgId(clientMsgId);
                session.send(err);
                return;
            }
            List<String> ok = new ArrayList<String>();
            List<String> denied = new ArrayList<String>();
            for (String topic : topics) {
                if (subscribing && !authorizer.canSubscribe(userId, topic)) {
                    denied.add(topic);
                } else {
                    ok.add(topic);
                }
            }
            if (!denied.isEmpty()) {
                RealtimeMessage err = RealtimeMessage.error(ERR_FORBIDDEN,
                        "无权限订阅: " + denied);
                err.setClientMsgId(clientMsgId);
                session.send(err);
            }
            // 订阅上限一次性判定：部分成功会让客户端看到 subscribed:[a,b] 却不知道 c,d 被丢了，
            // 逐个判定又会一帧一个错误。这里整帧不改状态，只报第一个越界的 topic。
            if (subscribing && !ok.isEmpty()) {
                String overflow = overflowTopicIfOverLimit(session, ok);
                if (overflow != null) {
                    RealtimeMessage err = RealtimeMessage.error(ERR_LIMIT,
                            "订阅后将超过单连接上限 z-msg.ws.max-topics-per-connection="
                                    + properties.getMaxTopicsPerConnection() + "，首个越界的 topic: " + overflow
                                    + "；本帧未做任何订阅变更");
                    err.setClientMsgId(clientMsgId);
                    session.send(err);
                    return;
                }
            }
            for (String topic : ok) {
                if (subscribing) {
                    registry.subscribe(session.getId(), topic);
                } else {
                    registry.unsubscribe(session.getId(), topic);
                }
            }
            if (!ok.isEmpty()) {
                Map<String, Object> payload = new java.util.LinkedHashMap<String, Object>();
                payload.put(subscribing ? "subscribed" : "unsubscribed", ok);
                payload.put("topics", new ArrayList<Object>(session.topics()));
                RealtimeMessage ack = RealtimeMessage.frame(RealtimeMessage.OP_ACK);
                ack.setClientMsgId(clientMsgId);
                ack.setPayload(MsgJson.toJson(payload));
                session.send(ack);
            }
            return;
        }
        if (RealtimeMessage.OP_PUBLISH.equals(op)) {
            String topic = str(frame.get("topic"));
            // 写侧默认拒绝：要放行必须由某个 TopicAuthorizationPolicy 明确表态，
            // 否则任意连接都能往别人的 room: / user: 里塞帧
            if (!authorizer.canPublish(userId, topic)) {
                RealtimeMessage err = RealtimeMessage.error(ERR_FORBIDDEN,
                        "没有向 " + topic + " 代发的权限（需模块注册 TopicAuthorizationPolicy）");
                err.setClientMsgId(clientMsgId);
                err.setTopic(topic);
                session.send(err);
                return;
            }
            Object payload = frame.get("payload");
            String kind = str(frame.get("kind"));
            int hit = publisher.publish(topic, kind == null ? RealtimeMessage.KIND_CHAT : kind, payload);
            Map<String, Object> info = new java.util.LinkedHashMap<String, Object>();
            info.put("topic", topic);
            info.put("delivered", hit);
            info.put("lastSeq", publisher.lastSeq(topic));
            RealtimeMessage ack = RealtimeMessage.frame(RealtimeMessage.OP_ACK);
            ack.setClientMsgId(clientMsgId);
            ack.setTopic(topic);
            ack.setPayload(MsgJson.toJson(info));
            session.send(ack);
            return;
        }
        RealtimeMessage unsupported = RealtimeMessage.error(ERR_UNSUPPORTED, "未知 op: " + op);
        unsupported.setClientMsgId(clientMsgId);
        session.send(unsupported);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
        MsgWsSession removed = registry.unregister(connectionId(raw));
        if (removed != null && log.isInfoEnabled()) {
            log.info("[z-msg-ws] 连接关闭 id={} userId={} status={} 在线={}",
                    removed.getId(), removed.getUserId(), status, registry.onlineConnections());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession raw, Throwable exception) {
        MsgWsSession removed = registry.unregister(connectionId(raw));
        if (removed != null) {
            log.warn("[z-msg-ws] 传输错误已摘除连接 id={} userId={} err={}",
                    removed.getId(), removed.getUserId(), exception.toString());
        }
    }

    /**
     * 一条连接能持有的 topic 数上限。没有这道闸，一个脚本连上后订阅
     * 几百万个 {@code room:x} 就能把注册表撑爆——每个 topic 都是一个 Set。
     *
     * @return 加上这批 topic 后会越界的那个 topic；不越界返回 null
     */
    private String overflowTopicIfOverLimit(MsgWsSession session, List<String> requested) {
        int limit = properties.getMaxTopicsPerConnection();
        if (limit <= 0) {
            return null;
        }
        int willAdd = 0;
        String firstNew = null;
        for (String topic : requested) {
            if (!session.subscribes(topic)) {
                if (firstNew == null) {
                    firstNew = topic;
                }
                willAdd++;
            }
        }
        return session.topics().size() + willAdd > limit ? firstNew : null;
    }

    private static List<String> readTopics(Object raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null) {
            return out;
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
            return out;
        }
        if (raw instanceof Collection) {
            for (Object o : (Collection<?>) raw) {
                if (o == null) {
                    continue;
                }
                String s = String.valueOf(o).trim();
                if (!s.isEmpty() && !out.contains(s)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static String connectionId(WebSocketSession raw) {
        Object v = raw.getAttributes().get(MsgHandshakeInterceptor.ATTR_CONNECTION_ID);
        return v == null ? null : String.valueOf(v);
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static void closeQuietly(WebSocketSession raw, CloseStatus status) {
        try {
            raw.close(status);
        } catch (Exception ignore) {
            // 已经关了
        }
    }
}
