package com.zifang.z.msg.ws.handshake;

import com.zifang.z.msg.core.realtime.RealtimeTicketService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * 握手鉴权：只认 {@code ?token=<ws-ticket>}，ticket 由 core 的
 * {@link RealtimeTicketService} 签发与校验。
 * <p>
 * 为什么不用 {@code Authorization} 头：浏览器的 {@code WebSocket} 构造器不能带自定义头，
 * 握手身份只能进 URL。正因为进了 URL（会被 access log 与代理日志留下），
 * 这里绝不能接受长期 JWT——所以设计成"先用已认证的 HTTP 会话换一个 60 秒一次性 ticket"。
 * 同理：本类任何日志都不打 token 原文。
 * <p>
 * 也不接受 {@code ?userId=1001} 这种"自称是谁"的参数：1.0.0 的越权面就是这么来的。
 */
public class MsgHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LogManager.getLogger(MsgHandshakeInterceptor.class);

    /**
     * 握手通过后写进 {@code WebSocketSession#getAttributes()} 的身份，后续帧只信这里
     */
    public static final String ATTR_USER_ID = "z-msg.ws.userId";
    public static final String ATTR_CONNECTION_ID = "z-msg.ws.connectionId";

    private static final String QUERY_TOKEN = "token";

    private final RealtimeTicketService tickets;
    private volatile boolean warnedUnconfigured;

    public MsgHandshakeInterceptor(RealtimeTicketService tickets) {
        this.tickets = tickets;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                  WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (tickets == null || !tickets.isConfigured()) {
            // 没配密钥就不开门：宁可连不上，也不能让握手变成无凭据通道
            warnUnconfigured();
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
        String token = queryParam(request.getURI(), QUERY_TOKEN);
        if (token == null || token.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Long userId = tickets.verify(token);
        if (userId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTR_USER_ID, userId);
        attributes.put(ATTR_CONNECTION_ID, java.util.UUID.randomUUID().toString());
        if (log.isInfoEnabled()) {
            log.info("[z-msg-ws] 握手通过 userId={} remote={}", userId,
                    request.getRemoteAddress() == null ? "?" : request.getRemoteAddress());
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需收尾：状态码在 beforeHandshake 里已经决定了
    }

    /**
     * 取 query 参数。不用 {@code UriComponentsBuilder.build().getQueryParams()}
     * 是因为它对同一个 key 出现多次时返回列表，这里必须"多值即非法"——
     * 否则 {@code ?token=a&token=b} 会有歧义。
     */
    private static String queryParam(URI uri, String name) {
        if (uri == null) {
            return null;
        }
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String found = null;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (!name.equals(pair.substring(0, eq))) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = urlDecode(pair.substring(eq + 1));
        }
        return found;
    }

    private static String urlDecode(String v) {
        try {
            return java.net.URLDecoder.decode(v, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 不可用", e);
        }
    }

    private void warnUnconfigured() {
        if (warnedUnconfigured) {
            return;
        }
        warnedUnconfigured = true;
        log.error("[z-msg-ws] 未配置 z-msg.realtime.ticket-secret，WebSocket 握手一律拒绝（503）。"
                + "配一把随机密钥后 ticket 才能签发，见 /api/msg/inbox/ws-token");
    }
}
