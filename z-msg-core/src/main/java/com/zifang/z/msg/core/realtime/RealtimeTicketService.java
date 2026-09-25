package com.zifang.z.msg.core.realtime;

import com.zifang.z.msg.core.config.MessageProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * WebSocket 一次性握手凭据（ws-ticket）。
 * <p>
 * 浏览器的 {@code WebSocket} 构造器不能带 {@code Authorization} 头，所以握手身份只能走 URL query。
 * 直接把长期 JWT 拼在 URL 上会被 access log、代理日志和浏览器历史原样留存 —— 这里改成
 * "先用已认证身份换一个 60 秒有效、只用于握手的短期 ticket"（{@code GET /api/msg/inbox/ws-token}）。
 * <p>
 * ticket 是自签自验的 HMAC 串，不查库、不存状态：过期即失效，伪造需要密钥。
 * 代价是无法主动吊销一个尚未过期的 ticket —— 所以 TTL 必须短，默认 60 秒只够完成一次握手。
 */
public class RealtimeTicketService {

    private static final Logger log = LogManager.getLogger(RealtimeTicketService.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = "|";
    private static final Base64.Encoder B64_ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DEC = Base64.getUrlDecoder();

    private final MessageProperties properties;
    private final SecureRandom random = new SecureRandom();

    public RealtimeTicketService(MessageProperties properties) {
        this.properties = properties;
    }

    /**
     * 密钥未配置时为 false；此时 {@link #issue(Long)} 一律返回 null，
     * 宿主必须显式配 {@code z-msg.realtime.ticket-secret}，不给可用的默认值。
     */
    public boolean isConfigured() {
        String secret = secret();
        return secret != null && !secret.trim().isEmpty();
    }

    /**
     * @return 形如 {@code <payloadB64>.<signatureB64>} 的 ticket；未配置密钥或 userId 为空时返回 null
     */
    public String issue(Long userId) {
        if (userId == null) {
            return null;
        }
        String secret = secret();
        if (secret == null || secret.trim().isEmpty()) {
            warnUnconfiguredOnce();
            return null;
        }
        long exp = System.currentTimeMillis() + ttlSeconds() * 1000L;
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        String payload = audience() + SEPARATOR + userId + SEPARATOR + exp + SEPARATOR
                + B64_ENC.encodeToString(nonce);
        byte[] sig = sign(payload, secret);
        return B64_ENC.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + B64_ENC.encodeToString(sig);
    }

    /**
     * @return ticket 绑定的 userId；签名不符、格式非法或已过期都返回 null（调用方据此拒绝握手）
     */
    public Long verify(String token) {
        String secret = secret();
        if (token == null || token.isEmpty() || secret == null || secret.trim().isEmpty()) {
            return null;
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return null;
        }
        String payload;
        byte[] presentedSig;
        try {
            payload = new String(B64_DEC.decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            presentedSig = B64_DEC.decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            // 非法 base64 属于常态输入（扫描、手抖），不当异常上报
            return null;
        }
        if (!MessageDigest.isEqual(sign(payload, secret), presentedSig)) {
            return null;
        }
        String[] parts = payload.split("\\" + SEPARATOR);
        if (parts.length != 4 || !audience().equals(parts[0])) {
            return null;
        }
        try {
            // exp 用毫秒：按秒截断比较会让 TTL=1s 的 ticket 实际活到 2s
            if (Long.parseLong(parts[2]) <= System.currentTimeMillis()) {
                return null;
            }
            return Long.valueOf(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 握手后连接持有的身份，ticket 本身不再参与后续帧校验。 */
    private String audience() {
        String aud = properties == null ? null : properties.getRealtime().getTicketAudience();
        return aud == null || aud.isEmpty() ? "z-msg-ws" : aud;
    }

    private int ttlSeconds() {
        int ttl = properties == null ? 60 : properties.getRealtime().getTicketTtlSeconds();
        // 配成 0/负数通常是笔误，而不是"想要一个永不过期的握手凭据"——那种东西不该存在
        return ttl > 0 ? ttl : 60;
    }

    private String secret() {
        return properties == null ? null : properties.getRealtime().getTicketSecret();
    }

    private volatile boolean warnedUnconfigured;

    private void warnUnconfiguredOnce() {
        if (warnedUnconfigured) {
            return;
        }
        warnedUnconfigured = true;
        log.error("[RealtimeTicketService] 未配置 z-msg.realtime.ticket-secret，ws-ticket 不签发；"
                + "WebSocket 握手将没有可用的短期凭据（请在 z-mist 里放一把随机密钥，不要用示例值）");
    }

    private static byte[] sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 不可用，JVM 环境异常", e);
        }
    }
}
