package com.zifang.z.msg.core.realtime;

import com.zifang.z.msg.core.config.MessageProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 一次性握手凭据（ws-ticket）。
 * <p>
 * 浏览器的 {@code WebSocket} 构造器不能带 {@code Authorization} 头，所以握手身份只能走 URL query。
 * 直接把长期 JWT 拼在 URL 上会被 access log、代理日志和浏览器历史原样留存 —— 这里改成
 * "先用已认证身份换一个 60 秒有效、只用于握手的短期 ticket"（{@code GET /api/msg/inbox/ws-token}）。
 * <p>
 * ticket 的校验分两个口子：{@link #verify(String)} 只判"这张票是不是真的、还没过期"，可以反复调；
 * {@link #consume(String)} 在此之上记账，让一张票只换得了第一次握手。握手走后者。
 * <p>
 * 代价说清楚：消费记录在进程内存里，所以 ① 重启后旧票在 TTL 内又能用一次 ② 多实例各记各的，
 * 同一张票在 N 个实例上各能握手一次 ③ 没配密钥之外没有任何"吊销"能力。这三条都换不来
 * "引入一张自建表"——握手凭据的泄露窗口本来就只有 60 秒，TTL 必须短才是正解。
 */
public class RealtimeTicketService {

    private static final Logger log = LogManager.getLogger(RealtimeTicketService.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = "|";
    private static final Base64.Encoder B64_ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DEC = Base64.getUrlDecoder();

    /**
     * 消费记录条数上限。只有"验签通过"的票才会进账，所以要灌满它得先有登录态去换票，
     * 2 万条已经覆盖一次 TTL 窗口内的正常握手量（约 1.5MB）。
     */
    static final int MAX_CONSUMED_TRACKED = 20_000;

    private final MessageProperties properties;
    private final SecureRandom random = new SecureRandom();
    /** jti（签发时播的 12 字节随机数）-> 该票的过期毫秒。过期的条目会被摘掉，所以不涨。 */
    private final ConcurrentHashMap<String, Long> consumed = new ConcurrentHashMap<String, Long>();
    private volatile long lastPruneMillis;

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
        Ticket t = check(token);
        return t == null ? null : t.userId;
    }

    /**
     * 一次性校验：只有这张票第一次被验通时才返回 userId，之后的重复使用一律返回 null。
     * <p>
     * 与 {@link #verify(String)} 分开，是因为"可重复验签"本身是有人要的能力（宿主的其它校验场景、
     * 以及签发方自己的自检），握手要的是另一种保证。
     *
     * @return 首次使用的 userId；无效、已过期或已被用过都是 null
     */
    public Long consume(String token) {
        Ticket t = check(token);
        if (t == null) {
            return null;
        }
        pruneExpired();
        if (consumed.putIfAbsent(t.jti, Long.valueOf(t.expMillis)) != null) {
            if (log.isInfoEnabled()) {
                log.info("[RealtimeTicketService] ticket 已被消费，重复使用被拒 userId={}", t.userId);
            }
            return null;
        }
        if (consumed.size() > MAX_CONSUMED_TRACKED) {
            evictOldestToMakeRoom();
        }
        return t.userId;
    }

    /** 验签 + 过期判定；通过后把"这张票是谁、什么时候过期、用哪个 jti 记账"一起带出来。 */
    private Ticket check(String token) {
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
            long exp = Long.parseLong(parts[2]);
            // exp 用毫秒：按秒截断比较会让 TTL=1s 的 ticket 实际活到 2s
            if (exp <= System.currentTimeMillis()) {
                return null;
            }
            return new Ticket(Long.valueOf(parts[1]), exp, jtiOf(parts));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * jti 就是签发时播的那 12 字节随机数：每次 {@link #issue(Long)} 都换，
     * 同一窗口内撞上的概率约 2^-80 量级，撞上的后果只是白拒一次新握手（拒的是刚验通的票，
     * 客户端换票重连即可），不会把两张不同的票判成同一张而放行重放。
     */
    private static String jtiOf(String[] parts) {
        return parts[3];
    }

    /**
     * 当前记账条数。只给"上限真的把住了"这一条断言用——没有它，那个上限就是一句注释。
     * 不是 public：宿主拿它没有能据此做出的决定。
     */
    int trackedConsumptions() {
        return consumed.size();
    }

    private void pruneExpired() {
        long now = System.currentTimeMillis();
        // 每握手一次就整表扫一遍是 O(n)：高流量下这是二次复杂度，所以一秒最多扫一次
        if (now - lastPruneMillis < 1000L) {
            return;
        }
        lastPruneMillis = now;
        for (Map.Entry<String, Long> e : consumed.entrySet()) {
            if (e.getValue().longValue() <= now) {
                consumed.remove(e.getKey(), e.getValue());
            }
        }
    }

    /**
     * 只有"在 TTL 窗口内成功握手超过 {@link #MAX_CONSUMED_TRACKED} 次"才走到这里。
     * 此时三个选项里没有一个是干净的：拒绝新握手等于把刷票变成宕机；静默不记账等于
     * 把这条保证悄悄撤掉。所以选第三个——丢掉最快过期的那些（它们的重放窗口已经最短）并留下日志。
     */
    private void evictOldestToMakeRoom() {
        final Map<String, Long> snapshot = new HashMap<String, Long>(consumed);
        List<String> keys = new ArrayList<String>(snapshot.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Long.compare(snapshot.get(a).longValue(), snapshot.get(b).longValue());
            }
        });
        int drop = consumed.size() - MAX_CONSUMED_TRACKED / 2;
        for (int i = 0; i < drop && i < keys.size(); i++) {
            consumed.remove(keys.get(i));
        }
        log.warn("[RealtimeTicketService] 一次性消费记录已达 {} 条上限，已丢弃 {} 张仍在有效期内的 ticket 记录："
                + "这些票在被再次使用前不再被拒。换票量这么大时请调小 z-msg.realtime.ticket-ttl-seconds"
                + "或把握手放到单一实例上", MAX_CONSUMED_TRACKED, drop);
    }

    /** 验通后的三样东西：身份、过期毫秒、记账用的 jti。 */
    private static final class Ticket {
        private final Long userId;
        private final long expMillis;
        private final String jti;

        private Ticket(Long userId, long expMillis, String jti) {
            this.userId = userId;
            this.expMillis = expMillis;
            this.jti = jti;
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
