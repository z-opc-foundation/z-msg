package com.zifang.z.msg.ws.handler;

import com.zifang.z.msg.api.RealtimeMessage;
import com.zifang.z.msg.api.RealtimePublisher;
import com.zifang.z.msg.api.RealtimeTopics;
import com.zifang.z.msg.core.json.MsgJson;
import com.zifang.z.msg.core.realtime.RealtimeTicketService;
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
 * 身份来自握手票（{@code ATTR_USER_ID}），之后每一帧都从 session 里取，
 * 绝不从帧内容里读 {@code userId}——否则任何客户端都能在帧里改成别人的 id。
 * 唯一能改身份的入口是 {@code op=auth}：它带的仍然是**一张新的、一次性的握手票**，
 * 走的仍然是 {@code RealtimeTicketService#consume}，所以"帧里能改身份"并不比握手松一寸；
 * 且默认关掉（{@code z-msg.ws.inband-auth-enabled=false}）。
 * <p>
 * 协议见 {@code z-msg/_doc/001_WS_PROTOCOL.md}。
 */
public class MsgWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LogManager.getLogger(MsgWebSocketHandler.class);

    private static final String ERR_BAD_FRAME = "WS_BAD_FRAME";
    private static final String ERR_FORBIDDEN = "WS_TOPIC_FORBIDDEN";
    private static final String ERR_UNSUPPORTED = "WS_OP_UNSUPPORTED";
    private static final String ERR_LIMIT = "WS_TOPIC_LIMIT";
    private static final String ERR_AUTH_DISABLED = "WS_AUTH_DISABLED";
    private static final String ERR_AUTH_FAILED = "WS_AUTH_FAILED";

    private final WsSessionRegistry registry;
    private final TopicAuthorizer authorizer;
    private final RealtimePublisher publisher;
    private final WsProperties properties;
    private final RealtimeTicketService tickets;

    public MsgWebSocketHandler(WsSessionRegistry registry, TopicAuthorizer authorizer,
                               RealtimePublisher publisher, WsProperties properties,
                               RealtimeTicketService tickets) {
        this.registry = registry;
        this.authorizer = authorizer;
        this.publisher = publisher;
        this.properties = properties;
        this.tickets = tickets;
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
        info.put("ops", clientOps(properties));
        info.put("limits", advertisedLimits(properties));
        ready.setPayload(MsgJson.toJson(info));
        if (!session.send(ready)) {
            registry.unregister(id);
        }
        if (log.isInfoEnabled()) {
            log.info("[z-msg-ws] 连接建立 id={} userId={} 自动订阅={} 在线={}",
                    id, userId, bound.size(), registry.onlineConnections());
        }
    }

    /**
     * {@code ready.ops}：服务端认的客户端 op 清单。
     * <p>
     * 这份清单是给前端读的，不是给文档抄的：接 z-msg 的页面不该靠翻源码判断"这条连接能不能发
     * {@code op=auth}"（1.2.0 及以前的发布件里根本没有这张帧，问了就是 {@code WS_OP_UNSUPPORTED}）。
     * 所以它的判据必须和分发处共用同一个开关，而不能是另一处手写死的名单。
     * 双向都有守卫，见 {@code MsgWsReadySelfDescriptionTest}：报出去的每一条 op 都真发一帧回去、
     * 断言拿到的不是 {@code WS_OP_UNSUPPORTED}；加了 {@code .equals(op)} 分支却没进清单的，由普查例拦下。
     */
    static List<String> clientOps(WsProperties properties) {
        List<String> ops = new ArrayList<String>();
        ops.add(RealtimeMessage.OP_PING);
        ops.add(RealtimeMessage.OP_SUBSCRIBE);
        ops.add(RealtimeMessage.OP_UNSUBSCRIBE);
        ops.add(RealtimeMessage.OP_PUBLISH);
        if (properties.isInbandAuthEnabled()) {
            ops.add(RealtimeMessage.OP_AUTH);
        }
        return ops;
    }

    /**
     * {@code ready.limits}：把资源上限报给客户端，让它不必把默认值抄成自己的常量。
     * <p>
     * 只报服务端**真的会执行**的那些：三个"0 或负数 = 不限"的字段（单连接 topic 上限、每用户连接
     * 上限、空闲超时）在关掉时**整个键缺席**。这不是省字节：报 {@code maxTopicsPerConnection: 0}
     * 会被前端读成"一个都不许订"，而实际语义是"没有这道闸"——一个键的缺席与一个 0，
     * 差着一个功能是被禁还是被查。
     * <p>
     * 值全部现取自已注入的 {@link WsProperties}：注册表的挤占、handler 的 topic 上限、
     * 容器的 buffer/idle 三个消费方拿的是同一个实例，所以"报的和执行的不一致"只可能是代码写错，
     * 而不是有两份真值源。
     */
    static Map<String, Object> advertisedLimits(WsProperties properties) {
        Map<String, Object> limits = new java.util.LinkedHashMap<String, Object>();
        if (properties.getMaxTopicsPerConnection() > 0) {
            limits.put("maxTopicsPerConnection", Integer.valueOf(properties.getMaxTopicsPerConnection()));
        }
        if (properties.getMaxSessionsPerUser() > 0) {
            limits.put("maxSessionsPerUser", Integer.valueOf(properties.getMaxSessionsPerUser()));
        }
        if (properties.getIdleTimeoutSeconds() > 0) {
            limits.put("idleTimeoutSeconds", Integer.valueOf(properties.getIdleTimeoutSeconds()));
        }
        limits.put("maxTextMessageBytes", Integer.valueOf(properties.getMaxTextMessageBytes()));
        return limits;
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
        if (RealtimeMessage.OP_AUTH.equals(op)) {
            handleAuth(session, str(frame.get("token")), clientMsgId);
            return;
        }
        RealtimeMessage unsupported = RealtimeMessage.error(ERR_UNSUPPORTED, "未知 op: " + op);
        unsupported.setClientMsgId(clientMsgId);
        session.send(unsupported);
    }

    /**
     * {@code {"op":"auth","token":"<新 ticket>"}} —— 在不重连的前提下重新证明一次身份。
     * <p>
     * 三条不可省的性质，逐条有测试钉着：
     * <ol>
     *   <li>票走 {@code consume}（一次性）而不是 {@code verify}，与握手同一把尺；验不过就
     *       {@code WS_AUTH_FAILED} 且**什么都不改**——包括不断线，一次手滑的坏票不该踢掉一条好连接；</li>
     *   <li>身份变了就把现役 topic 拿**新身份**逐条复核，不过的退订。默认授权策略里
     *       {@code user:<旧 id>} 必然被拒，所以旧用户的红点会自动断掉，不会跟着连接走；</li>
     *   <li>换身份要连带搬注册表的按用户索引（{@link WsSessionRegistry#rebindIdentity}），
     *       并在新用户名下重跑 {@code max-sessions-per-user} 挤占。</li>
     * </ol>
     * 任何分支都不打 token 原文。
     */
    private void handleAuth(MsgWsSession session, String token, String clientMsgId) {
        if (!properties.isInbandAuthEnabled()) {
            session.send(authError(clientMsgId, ERR_AUTH_DISABLED,
                    "带内换身份未开启，需要 z-msg.ws.inband-auth-enabled=true（默认 false）"));
            return;
        }
        if (tickets == null || !tickets.isConfigured()) {
            // 与握手同样的 fail closed：没配密钥就没有可验的票，而不是"跳过校验"
            session.send(authError(clientMsgId, ERR_AUTH_FAILED, "实时票证未配置，无法校验 auth 帧"));
            return;
        }
        if (token == null || token.trim().isEmpty()) {
            session.send(authError(clientMsgId, ERR_BAD_FRAME,
                    "auth 帧要带 token，形如 {\"op\":\"auth\",\"token\":\"<ws-ticket>\"}"));
            return;
        }
        Long newUserId = tickets.consume(token);
        if (newUserId == null) {
            // 签名不对 / 已过期 / 这张票刚才已经用过 —— 三种情况刻意不区分，同握手的 401
            session.send(authError(clientMsgId, ERR_AUTH_FAILED, "ticket 无效、已过期或已被使用"));
            return;
        }
        Long before = session.getUserId();
        boolean hadOldInbox = before != null && session.subscribes(RealtimeTopics.user(before));
        Long previous = registry.rebindIdentity(session.getId(), newUserId);
        if (registry.find(session.getId()) == null) {
            // 连接在两次读之间被摘掉了（对端刚断线或被挤占），不必再往下复核与回帧
            return;
        }
        boolean changed = previous != null && !previous.equals(newUserId);
        List<String> revoked = new ArrayList<String>();
        if (changed) {
            // 旧身份那条 user:<旧id> 由 rebindIdentity 连带摘掉（它只在这里记账一次，好让客户端看得见）
            if (hadOldInbox) {
                revoked.add(RealtimeTopics.user(previous));
            }
            for (String topic : new ArrayList<String>(session.topics())) {
                if (!authorizer.canSubscribe(newUserId, topic)) {
                    registry.unsubscribe(session.getId(), topic);
                    revoked.add(topic);
                }
            }
            String ownInbox = RealtimeTopics.user(newUserId);
            if (authorizer.canSubscribe(newUserId, ownInbox)) {
                registry.subscribe(session.getId(), ownInbox);
            }
        }
        session.touch();

        Map<String, Object> payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("changed", changed);
        payload.put("userId", newUserId);
        if (changed) {
            payload.put("previousUserId", previous);
            payload.put("revoked", revoked);
        }
        payload.put("topics", new ArrayList<Object>(session.topics()));
        RealtimeMessage ack = RealtimeMessage.frame(RealtimeMessage.OP_ACK);
        ack.setClientMsgId(clientMsgId);
        ack.setPayload(MsgJson.toJson(payload));
        if (log.isInfoEnabled()) {
            log.info("[z-msg-ws] 带内换票 id={} {}->{} 复核后取消订阅={} 在线={}",
                    session.getId(), previous, newUserId, revoked.size(), registry.onlineConnections());
        }
        session.send(ack);
    }

    private static RealtimeMessage authError(String clientMsgId, String code, String message) {
        RealtimeMessage err = RealtimeMessage.error(code, message);
        err.setClientMsgId(clientMsgId);
        return err;
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
