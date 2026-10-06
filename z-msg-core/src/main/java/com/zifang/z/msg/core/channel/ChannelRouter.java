package com.zifang.z.msg.core.channel;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.FanOutResult;
import com.zifang.z.msg.api.FanOutResult.Outcome;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.core.config.MessageProperties;
import com.zifang.z.msg.core.domain.entity.MsgDeliveryLogDO;
import com.zifang.z.msg.core.domain.mapper.MsgDeliveryLogMapper;
import com.zifang.z.msg.core.domain.service.MsgUserPreferenceService;
import com.zifang.z.msg.core.ratelimit.ChannelRateLimiter;
import com.zifang.z.msg.core.sender.SenderRegistry;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渠道路由器：一个业务事件 → 一条（或一组）通道投递
 * <p>
 * 1.0.0 → 1.1.0 修掉的四个真问题：
 * <ol>
 *   <li>dispatch 的 switch 里没有 IN_APP 分支，而批量任务默认通道就是 IN_APP，
 *       等于默认路径必然 UNSUPPORTED_CHANNEL；现在 IN_APP 只是 {@link InAppChannel}
 *       注册进来的一个普通 provider。</li>
 *   <li>限流器是 {@code new ChannelRateLimiter(50)} 的私有字段，既不读配置也不是 bean；
 *       现在按通道配额 + 每用户每分钟配额两层，全部来自 yml。</li>
 *   <li>重试固定 3 次、退避 1s/2s/4s 并把 HTTP 线程钉住，且 retryCount 永远写 0；
 *       现在次数/退避/不可重试错误码可配，且有 maxBlockingMs 上限，实际尝试次数落库。</li>
 *   <li>provider 与 durationMs 两个字段从来没写过，投递日志看不出是谁发、发多久；现在都写。</li>
 * </ol>
 */
@Component
public class ChannelRouter {

    private static final Logger log = LogManager.getLogger(ChannelRouter.class);

    @Resource
    private MessageTemplateEngine templateEngine;
    @Resource
    private MsgUserPreferenceService preferenceService;
    @Resource
    private MsgDeliveryLogMapper deliveryLogMapper;
    @Resource
    private SenderRegistry senderRegistry;
    @Resource
    private MessageProperties properties;
    /**
     * 限流器由 {@code MsgCoreConfiguration} 按配置建 bean 注入（1.0.0 是 new 出来的）
     */
    @Resource
    private ChannelRateLimiter rateLimiter;

    /**
     * 出站幂等的<b>本机快路径</b>：key -> 过期时间戳。
     *
     * <p><b>仅本 JVM 有效</b>，多实例部署时它拦不住别人发过的消息。跨节点的那一半在
     * {@code z_msg_delivery_log.idempotency_key} + {@code uk_channel_biz_dedup} 唯一索引上：
     * 发送<b>前</b>先插一行占住幂等位，撞索引即判 DUPLICATED（见 {@link #claimIdemSlot}）。
     * 这一层是权威的，本表只是一道省掉一次数据库往返的快路径。</p>
     *
     * <p>两层用<b>同一个</b>去重令牌（{@link #dedupToken}），不要各拼各的 ——
     * 拼法一旦分叉，同一份消息在本机会判"不重复"、在别的节点会判"重复"，
     * 于是重放行为取决于它落在哪台机器上。</p>
     */
    private final ConcurrentHashMap<String, Long> issued = new ConcurrentHashMap<>();

    /**
     * {@code z_msg_delivery_log.status} 里的 pending：占位行已插、厂商还没调。
     * 只有 ChannelRouter 会写这个值，落库行只会是 1/2/3/4。
     */
    private static final int IDEM_STATUS_PENDING = 0;

    /**
     * 一次投递在"占位 → 落地"之间携带的东西。
     *
     * <ul>
     *   <li>{@link #none()}：没有幂等键，或占位这一步不可用（库还没升级等）→ 照常投递，不去重；</li>
     *   <li>{@link #duplicate()}：别的节点已经占住 → 本次直接判 DUPLICATED，一个字都不发；</li>
     *   <li>{@link #of}：本节点已插占位行 → 落地时就地 UPDATE 那一行；</li>
     *   <li>{@link #fresh}：本节点没有占位行（降级通道那一行）→ 落地时新插一行并带上幂等位。</li>
     * </ul>
     */
    private static final class Claim {
        private static final Claim NONE = new Claim(null, false, false);
        private static final Claim DUPLICATE = new Claim(null, true, false);
        private final String token;
        private final boolean duplicate;
        /** true = 占位行已经插进库里了，落地要靠 UPDATE 去认领它。 */
        private final boolean placeholder;

        private Claim(String token, boolean duplicate, boolean placeholder) {
            this.token = token;
            this.duplicate = duplicate;
            this.placeholder = placeholder;
        }

        static Claim none() {
            return NONE;
        }

        static Claim duplicate() {
            return DUPLICATE;
        }

        static Claim of(String token) {
            return new Claim(token, false, true);
        }

        static Claim fresh(String token) {
            return new Claim(token, false, false);
        }
    }

    // ==================== 单通道 ====================

    /**
     * 单通道投递（走完整管道：偏好 → 限流 → 渲染 → 重试 → 日志）
     */
    public MessageSendResult route(Message message) {
        Outcome o = deliver(message, true);
        if (delivered(o)) {
            return MessageSendResult.ok(o.getProvider(), o.getProviderMessageId());
        }
        return MessageSendResult.fail(o.getProvider(), o.getErrorCode(), o.getErrorMessage());
    }

    // ==================== 多通道 fan-out ====================

    /**
     * fan-out 主入口：一个事件 → 多个渠道
     *
     * @param bizType   业务类型
     * @param userId    接收用户
     * @param receivers 各渠道的接收方 {"SMS":"138...","EMAIL":"a@b.com","IM_DINGTALK":"robot"}
     * @param params    模板渲染参数
     * @param locale    多语言
     */
    public FanOutResult fanOut(String bizType, Long userId, Map<String, String> receivers,
                               Map<String, String> params, String locale) {
        Message base = Message.builder()
                .bizType(bizType)
                .userId(userId)
                .params(params)
                .locale(locale)
                .build();
        return fanOut(base, receivers);
    }

    /**
     * 以一个已填好内容/参数的消息为模板，向多个通道 fan-out。
     * 每个通道的 receiver 由 map 提供（IN_APP 若缺省则取 userId）。
     */
    public FanOutResult fanOut(Message base, Map<String, String> receivers) {
        FanOutResult result = new FanOutResult(base.getBizType(), base.getUserId());
        if (receivers == null || receivers.isEmpty()) {
            log.debug("[ChannelRouter] fanOut 无 receiver，跳过 bizType={}", base.getBizType());
            return result;
        }
        for (Map.Entry<String, String> e : receivers.entrySet()) {
            String channel = Channels.normalize(e.getKey());
            Message m = base.copyForChannel(channel, e.getValue());
            if (Channels.IN_APP.equals(channel) && m.getReceiver() == null && base.getUserId() != null) {
                m.setReceiver(String.valueOf(base.getUserId()));
            }
            result.add(deliver(m, true));
        }
        return result;
    }

    /**
     * 按配置里声明的通道集合自动 fan-out（业务侧只给 userId 时用这个）
     *
     * @param receiverResolver 提供 channel -> receiver（如从用户档案取手机号/邮箱）
     */
    public FanOutResult fanOutChannels(Message base, List<String> channels,
                                       java.util.function.Function<String, String> receiverResolver) {
        Map<String, String> receivers = new java.util.LinkedHashMap<>();
        for (String c : channels) {
            String r = receiverResolver.apply(c);
            if (r != null && !r.isEmpty()) {
                receivers.put(c, r);
            }
        }
        return fanOut(base, receivers);
    }

    // ==================== 核心投递 ====================

    private Outcome deliver(Message message, boolean allowFallback) {
        long start = System.currentTimeMillis();
        String channel = Channels.normalize(message.getChannel());
        if (channel == null || !Channels.isKnown(channel)) {
            return finish(message, Outcome.skipped(channel, "UNSUPPORTED_CHANNEL", "不支持的渠道: " + channel),
                    start, null, 0);
        }
        message.setChannel(channel);

        // 1. 幂等：同 (channel, bizType, idempotencyKey) 只发一次
        String idemKey = dedupToken(message);
        if (idemKey != null && isDuplicate(idemKey)) {
            return finish(message, Outcome.skipped(channel, "DUPLICATED", "幂等命中，已跳过"),
                    start, null, 0);
        }

        // 2. 用户偏好 + 静默时段（紧急消息跳过，见 Message.priority）
        Long userId = message.getUserId();
        if (userId != null && !message.isUrgent()) {
            Set<String> allowed = safeAllowedChannels(userId, message.getBizType());
            if (allowed != null && !allowed.contains(channel)) {
                return finish(message, Outcome.skipped(channel, "PREF_BLOCKED", "用户偏好屏蔽"),
                        start, null, 0);
            }
            if (inQuietHours(userId, message.getBizType())) {
                return finish(message, Outcome.skipped(channel, "QUIET_HOURS", "静默时段"),
                        start, null, 0);
            }
        }

        // 3. 限流：通道级 + 每用户级
        MessageProperties.RateLimit rl = properties.getRateLimit();
        if (rl.isEnabled()) {
            if (!rateLimiter.tryAcquire(channel)) {
                return finish(message, Outcome.skipped(channel, "RATE_LIMIT", "通道限流"),
                        start, null, 0);
            }
            int perUser = rl.getPerUserPermitsPerMinute();
            if (perUser > 0 && userId != null
                    && !rateLimiter.tryAcquire(channel + "#u" + userId, perUser, 60_000L)) {
                return finish(message, Outcome.skipped(channel, "USER_RATE_LIMIT", "单用户限流"),
                        start, null, 0);
            }
        }

        // 4. 渲染（业务已给 subject/content 则不覆盖）
        Long templateId = renderInPlace(message);

        // 5. 发送**前**占住幂等位。
        //    顺序不能挪到发送之后：唯一索引只在写日志那一刻才生效，那时消息早发出去了，
        //    "跨节点只发一次"就成了一句空话。占位行即最终行 —— 落地时 UPDATE 它，不另插。
        Claim claim = claimIdemSlot(message, idemKey);
        if (claim.duplicate) {
            // 别的节点先到了。它可能正在发，也可能发完就留下这一行；
            // 两种情况下本节点都不该再发一遍。本机快路径也顺手记上，省得下次又去撞索引。
            if (idemKey != null) {
                markIssued(idemKey);
            }
            return finish(message, Outcome.skipped(channel, "DUPLICATED", "幂等命中（其他节点已发出），已跳过"),
                    start, null, 0);
        }

        // 6. 发送 + 重试 + 降级
        Outcome outcome = dispatchWithRetry(message);
        if (outcome.getStatus() == FanOutResult.STATUS_FAILED && allowFallback) {
            Outcome fb = tryFallback(message, outcome, start, idemKey);
            if (fb != null) {
                // 降级已经把消息送到用户手里了：占位。这里以前直接 return，
                // 于是"送达了的"不占位、"没送达的"反倒占位，两头都反了
                if (idemKey != null) {
                    markIssued(idemKey);
                }
                // 幂等位跟着"真正送达的那一行"走，所以主通道这行落地时要把位让出去；
                // 顺带把主通道的失败补记下来（降级成功时过去压根没记这一行，
                // 于是投递日志里看不出这次为什么走了降级）。
                finish(message, outcome, start, templateId, outcome.getAttempts(), claim);
                return fb;
            }
        }
        if (idemKey != null && delivered(outcome)) {
            markIssued(idemKey);
        }
        return finish(message, outcome, start, templateId, outcome.getAttempts(), claim);
    }

    /**
     * 这一次到底有没有把消息送出去。
     * <p>只有真送出去了才配占幂等位：厂商故障、限流、偏好屏蔽这些一律不占，
     * 否则一次网络抖动就把这条消息钉死在 24 小时窗口里，重放也发不出去。</p>
     */
    private static boolean delivered(Outcome o) {
        return o.getStatus() == FanOutResult.STATUS_SUCCESS
                || o.getStatus() == FanOutResult.STATUS_MOCK;
    }

    private Outcome dispatchWithRetry(Message message) {
        MessageProperties.Retry r = properties.getRetry();
        int maxAttempts = Math.max(0, r.getMaxAttempts());
        long deadline = System.currentTimeMillis() + Math.max(0, r.getMaxBlockingMs());
        ChannelSender sender = senderRegistry.pick(message.getChannel());
        Outcome last = null;
        for (int attempt = 0; attempt <= maxAttempts; attempt++) {
            long eachStart = System.currentTimeMillis();
            last = attemptSend(sender, message);
            last.setAttempts(attempt + 1);
            if (last.getStatus() == FanOutResult.STATUS_SUCCESS
                    || last.getStatus() == FanOutResult.STATUS_MOCK
                    || last.getStatus() == FanOutResult.STATUS_SKIPPED) {
                return last;
            }
            if (attempt == maxAttempts || !retryable(last.getErrorCode(), r)) {
                return last;
            }
            long backoff = backoffMs(r, attempt);
            if (System.currentTimeMillis() + backoff > deadline) {
                log.warn("[ChannelRouter] 重试预算耗尽 channel={} attempts={} err={}",
                        message.getChannel(), attempt + 1, last.getErrorCode());
                return last;
            }
            log.warn("[ChannelRouter] dispatch failed attempt={}/{} channel={} receiver={} retryIn={}ms err={}",
                    attempt + 1, maxAttempts, message.getChannel(), message.getReceiver(),
                    backoff, last.getErrorMessage());
            sleep(backoff);
        }
        return last;
    }

    /**
     * 一次真正的 provider 调用。这里不抛异常——任何异常都转成 failed outcome，
     * 保证调用方（以及批量任务的逐条统计）拿到的永远是结构化结果。
     */
    private Outcome attemptSend(ChannelSender sender, Message message) {
        long start = System.currentTimeMillis();
        if (!sender.ready()) {
            Outcome notConfigured = Outcome.skipped(message.getChannel(), "PROVIDER_NOT_CONFIGURED",
                    "provider " + sender.provider() + " 配置不完整");
            // provider 列必须填：这一行存在的意义就是"哪一个 provider 没配齐"，
            // 空着的话投递日志里最该看的那一类反而查不出来
            notConfigured.setProvider(sender.provider());
            return notConfigured;
        }
        MessageSendResult res;
        try {
            res = sender.send(message);
        } catch (Exception e) {
            log.error("[ChannelRouter] provider 抛异常 channel={} provider={} err={}",
                    message.getChannel(), sender.provider(), e.toString());
            res = MessageSendResult.fail(sender.provider(), "PROVIDER_EXCEPTION", e.getMessage());
        }
        if (res == null) {
            res = MessageSendResult.fail(sender.provider(), "PROVIDER_NULL_RESULT", "provider 返回 null");
        }
        Outcome o = new Outcome();
        o.setChannel(message.getChannel());
        o.setProvider(res.getProvider() != null ? res.getProvider() : sender.provider());
        o.setReceiver(message.getReceiver());
        o.setDurationMs(System.currentTimeMillis() - start);
        if (res.isSuccess()) {
            // mock provider 的"成功"记 3 不记 1：链路要能跑通，但统计不能被假成功灌满
            o.setStatus(sender.isMock() ? FanOutResult.STATUS_MOCK : FanOutResult.STATUS_SUCCESS);
            o.setProviderMessageId(res.getProviderMessageId());
        } else {
            o.setStatus(FanOutResult.STATUS_FAILED);
            o.setErrorCode(res.getErrorCode());
            o.setErrorMessage(res.getErrorMessage());
        }
        return o;
    }

    /**
     * 主通道失败后按 {@code z-msg.channel.<X>.fallback-channels} 顺序降级
     *
     * @param idemKey 去重令牌：降级成功时**由降级这一行**持有它（真正送达的是它）
     */
    private Outcome tryFallback(Message failed, Outcome original, long fanOutStart, String idemKey) {
        List<String> chain = properties.channelCfg(failed.getChannel()).getFallbackChannels();
        if (chain == null || chain.isEmpty()) {
            return null;
        }
        for (String ch : chain) {
            String channel = Channels.normalize(ch);
            if (channel == null || channel.equals(failed.getChannel()) || !Channels.isKnown(channel)) {
                continue;
            }
            ChannelSender sender = senderRegistry.pick(channel);
            if (!sender.ready() || sender.isMock()) {
                continue;
            }
            String receiver = fallbackReceiver(channel, failed);
            if (receiver == null) {
                continue;
            }
            Message m = failed.copyForChannel(channel, receiver);
            Outcome o = attemptSend(sender, m);
            o.setAttempts(1);
            if (o.getStatus() == FanOutResult.STATUS_SUCCESS) {
                log.warn("[ChannelRouter] {} 失败已降级到 {} (原错误 {})",
                        failed.getChannel(), channel, original.getErrorCode());
                return finish(m, o, fanOutStart, null, 1,
                        idemKey == null ? Claim.none() : Claim.fresh(idemKey));
            }
        }
        return null;
    }

    private String fallbackReceiver(String channel, Message failed) {
        if (Channels.IN_APP.equals(channel)) {
            return failed.getUserId() == null ? null : String.valueOf(failed.getUserId());
        }
        if (Channels.EMAIL.equals(channel)) {
            return failed.param("email", null);
        }
        if (Channels.SMS.equals(channel)) {
            return failed.param("phone", null);
        }
        return failed.param("receiver:" + channel.toLowerCase(), null);
    }

    // ==================== 渲染 / 日志 ====================

    private Long renderInPlace(Message message) {
        if (notBlank(message.getSubject()) && notBlank(message.getContent())) {
            return null;
        }
        MessageTemplateEngine.RenderedTemplate t;
        try {
            t = templateEngine.renderWithLocale(message.getBizType(), message.getChannel(),
                    message.getLocale(), message.getParams());
        } catch (Exception e) {
            log.warn("[ChannelRouter] 模板渲染失败 bizType={} channel={} err={}",
                    message.getBizType(), message.getChannel(), e.toString());
            return null;
        }
        if (t == null) {
            return null;
        }
        if (!notBlank(message.getSubject())) {
            message.setSubject(t.getSubject());
        }
        if (!notBlank(message.getContent())) {
            message.setContent(t.getContent());
        }
        return t.getTemplateId();
    }

    private Outcome finish(Message message, Outcome outcome, long start, Long templateId, int attempts) {
        return finish(message, outcome, start, templateId, attempts, null);
    }

    /**
     * @param claim 发送前占位的幂等位；非空时落地是"认领占位行"而不是"另插一行"
     */
    private Outcome finish(Message message, Outcome outcome, long start, Long templateId, int attempts,
                           Claim claim) {
        outcome.setTemplateId(templateId);
        if (outcome.getAttempts() <= 0) {
            outcome.setAttempts(Math.max(1, attempts));
        }
        if (outcome.getDurationMs() <= 0) {
            outcome.setDurationMs(System.currentTimeMillis() - start);
        }
        logDelivery(message, outcome, templateId, claim);
        return outcome;
    }

    private void logDelivery(Message message, Outcome o, Long templateId, Claim claim) {
        try {
            MsgDeliveryLogDO row = new MsgDeliveryLogDO();
            row.setBizType(message.getBizType());
            row.setChannel(o.getChannel());
            row.setProvider(o.getProvider());
            row.setReceiver(o.getReceiver() == null ? message.getReceiver() : o.getReceiver());
            row.setUserId(message.getUserId());
            row.setTemplateId(templateId);
            row.setRenderedSubject(message.getSubject());
            row.setRenderedContent(truncate(message.getContent(), 2000));
            row.setProviderMessageId(o.getProviderMessageId());
            row.setStatus(o.getStatus());
            row.setErrorCode(o.getErrorCode());
            row.setErrorMessage(truncate(o.getErrorMessage(), 500));
            row.setRetryCount(Math.max(0, o.getAttempts() - 1));
            row.setMaxRetry(properties.getRetry().getMaxAttempts());
            row.setDurationMs((int) Math.min(Integer.MAX_VALUE, o.getDurationMs()));
            row.setTenantCode(message.getTenantCode());
            row.setDomainCode(message.getDomainCode());
            row.setCreatedTime(LocalDateTime.now());
            row.setUpdatedTime(LocalDateTime.now());

            // 只有真送出去的那一行才配持有幂等位。这与"占位行即最终行"是一对：
            // 没送达就把位还回去，否则一次厂商抖动就把这条消息永久钉死，运维重放也发不出去。
            boolean holdsIdem = claim != null && claim.token != null && delivered(o);
            if (claim != null && claim.placeholder && claim.token != null) {
                if (deliveryLogMapper.update(null, finalizeWrapper(row, holdsIdem, claim.token)) > 0) {
                    return;
                }
                // 落到这儿说明占位行已经不在了（多半是被清理脚本删过）。
                // 退化成普通日志行；幂等位让给下面这次 INSERT，别去抢那一行。
                holdsIdem = false;
            }
            row.setIdempotencyKey(holdsIdem ? claim.token : null);
            deliveryLogMapper.insert(row);
        } catch (Exception ex) {
            log.error("[ChannelRouter] logDelivery 失败: {}", ex.toString());
        }
    }

    /**
     * 认领占位行：把它就地改写成最终那一行。
     *
     * <p>按 {@code (channel, bizType, idempotencyKey)} 定位而不是按主键 —— 唯一索引保证这个
     * 三元组至多对应一行，所以不必依赖驱动把自增主键回填回来（回填不回来的话按 id 更新
     * 会静默更新 0 行，看起来"落库了"其实那一行还停在 pending）。</p>
     *
     * <p>{@code status = IDEM_STATUS_PENDING} 这个条件让"占位 → 转正"是一次真正的状态迁移：
     * 已经被别人转正过的行不会被二次改写。</p>
     */
    private UpdateWrapper<MsgDeliveryLogDO> finalizeWrapper(MsgDeliveryLogDO row, boolean holdsIdem,
                                                              String token) {
        UpdateWrapper<MsgDeliveryLogDO> w = new UpdateWrapper<>();
        w.set("status", row.getStatus());
        w.set("retry_count", row.getRetryCount());
        w.set("max_retry", row.getMaxRetry());
        w.set("updated_time", row.getUpdatedTime());
        // 占位行是空的，null 字段不 set 就天然还是 null —— 全程不把 null 绑进参数，
        // 免得 jdbcTypeForNull（默认 OTHER）在某些驱动上被拒。
        setIfNotNull(w, "provider", row.getProvider());
        setIfNotNull(w, "receiver", row.getReceiver());
        setIfNotNull(w, "user_id", row.getUserId());
        setIfNotNull(w, "template_id", row.getTemplateId());
        setIfNotNull(w, "rendered_subject", row.getRenderedSubject());
        setIfNotNull(w, "rendered_content", row.getRenderedContent());
        setIfNotNull(w, "provider_message_id", row.getProviderMessageId());
        setIfNotNull(w, "error_code", row.getErrorCode());
        setIfNotNull(w, "error_message", row.getErrorMessage());
        setIfNotNull(w, "duration_ms", row.getDurationMs());
        setIfNotNull(w, "tenant_code", row.getTenantCode());
        setIfNotNull(w, "domain_code", row.getDomainCode());
        if (!holdsIdem) {
            // 没送达 → 把位还回去。用 SQL 字面量而不是 set(col, null)：后者要走参数绑定，
            // 而 MP 默认 updateById / set 的 null 字段压根不生成 SET 子句（NOT_NULL 策略），
            // 位会赖在那一行上永远不放。
            w.setSql("idempotency_key = null");
        }
        w.eq("channel", row.getChannel());
        w.eq("biz_type", row.getBizType());
        w.eq("idempotency_key", token);
        w.eq("status", IDEM_STATUS_PENDING);
        return w;
    }

    private static void setIfNotNull(UpdateWrapper<MsgDeliveryLogDO> w, String column, Object value) {
        if (value != null) {
            w.set(column, value);
        }
    }

    // ==================== 幂等 ====================

    /**
     * 出站去重令牌：本机幂等表与 {@code uk_channel_biz_dedup} 唯一索引<b>共用</b>这一份拼法。
     *
     * <p>带上 {@code userId} / {@code receiver}：裸 key 单独不唯一 —— 同一个 dedupKey 本来
     * 就可能发给多个用户（{@code InAppChannel} 判站内信幂等时也是按 (userId, dedupKey) 而不是
     * dedupKey 单列，理由写在那边）。{@code Message.idempotencyKey} 的 Javadoc 只写了
     * 「同 channel + bizType + key」，比实现窄，照字面收窄会让"一份日报发给 1000 个用户"只发出去 1 条。</p>
     *
     * <p>null 一律编码成 {@code "-"} 而不是留空：唯一索引里任何一列为 NULL 整行都不参与去重，
     * 那样"没填 userId 的消息"就彻底失去跨节点幂等了，而且是静默失效。</p>
     */
    private String dedupToken(Message message) {
        String k = message.getIdempotencyKey();
        if (!notBlank(k)) {
            return null;
        }
        String token = message.getChannel() + "|" + message.getBizType() + "|" + k
                + "|" + (message.getUserId() == null ? "-" : message.getUserId())
                + "|" + (message.getReceiver() == null ? "-" : message.getReceiver());
        return fold(token, IDEMPOTENCY_KEY_MAX);
    }

    /**
     * 发送前占位：插一行 pending 抢 {@code uk_channel_biz_dedup}。
     *
     * <p>撞唯一索引 = 别的节点已经抢到了 = 本次别发。任何<b>其它</b>故障（库还没升级、
     * 连接断了、权限不足）都返回 {@link Claim#none()} 放行 —— 幂等是增强项，
     * 不能因为它不可用就把消息一起发不出去。</p>
     */
    private Claim claimIdemSlot(Message message, String token) {
        if (token == null) {
            return Claim.none();
        }
        if (!notBlank(message.getBizType())) {
            // 索引里 biz_type 是可空列，它为 NULL 时这一行压根不参与去重，占了也白占
            log.warn("[ChannelRouter] bizType 为空，跨节点幂等退化为仅本机 channel={}",
                    message.getChannel());
            return Claim.none();
        }
        try {
            MsgDeliveryLogDO row = new MsgDeliveryLogDO();
            row.setBizType(message.getBizType());
            row.setChannel(message.getChannel());
            row.setIdempotencyKey(token);
            row.setStatus(IDEM_STATUS_PENDING);
            row.setRetryCount(0);
            row.setMaxRetry(properties.getRetry().getMaxAttempts());
            row.setProvider(providerOf(message.getChannel()));
            row.setReceiver(message.getReceiver());
            row.setCreatedTime(LocalDateTime.now());
            row.setUpdatedTime(LocalDateTime.now());
            deliveryLogMapper.insert(row);
            return Claim.of(token);
        } catch (Exception e) {
            if (isDuplicateKey(e)) {
                return Claim.duplicate();
            }
            // 只认 DuplicateKeyException，不靠 SQLState 兜底：MySQL 里 NOT NULL 违约同样是
            // 23000，把它误判成"重复"就会静默吞掉消息。上面的占位行把 NOT NULL 列都填满了，
            // 真正能撞到的约束只剩唯一索引这一条。
            log.warn("[ChannelRouter] 跨节点幂等占位不可用，降级为仅本机去重 channel={} err={}",
                    message.getChannel(), e.toString());
            return Claim.none();
        }
    }

    /** 唯一键违约在 Spring 里是 DuplicateKeyException；可能被包了好几层，往下找一层。 */
    private static boolean isDuplicateKey(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof DuplicateKeyException) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }

    private String providerOf(String channel) {
        ChannelSender sender = senderRegistry.pick(channel);
        String provider = sender == null ? null : sender.provider();
        return notBlank(provider) ? provider : "unknown";
    }

    /**
     * 超长令牌折叠：截断会毁掉唯一性（两个不同的令牌可能截成同一个）。
     * 所以保留可读前缀 + "#" + 全文 SHA-256，长度严格落在列宽内。
     */
    private static String fold(String token, int max) {
        if (token.length() <= max) {
            return token;
        }
        int keep = Math.max(1, max - 1 - 64);
        return token.substring(0, keep) + "#" + sha256Hex(token);
    }

    private static String sha256Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private boolean isDuplicate(String key) {
        sweepExpired();
        Long expireAt = issued.get(key);
        return expireAt != null && expireAt > System.currentTimeMillis();
    }

    private void markIssued(String key) {
        issued.put(key, System.currentTimeMillis() + IDEMPOTENCY_TTL_MS);
    }

    private void sweepExpired() {
        if (issued.size() < 1024) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = issued.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < now) {
                it.remove();
            }
        }
    }

    private static final long IDEMPOTENCY_TTL_MS = 24L * 3600 * 1000;

    /** 与 DDL 里 {@code idempotency_key VARCHAR(191)} 对齐；超长令牌走 SHA-256 折叠。 */
    private static final int IDEMPOTENCY_KEY_MAX = 191;

    /**
     * 测试用：清空本机幂等表
     */
    public void clearIdempotencyCache() {
        issued.clear();
    }

    // ==================== 小工具 ====================

    private Set<String> safeAllowedChannels(Long userId, String bizType) {
        try {
            return preferenceService.getAllowedChannels(userId, bizType);
        } catch (Exception e) {
            log.warn("[ChannelRouter] 读偏好失败，按不限制处理 userId={} err={}", userId, e.toString());
            return Collections.emptySet();
        }
    }

    private boolean inQuietHours(Long userId, String bizType) {
        try {
            return preferenceService.isInQuietHours(userId, bizType);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean retryable(String errorCode, MessageProperties.Retry r) {
        if (errorCode == null) {
            return true;
        }
        for (String code : r.getNonRetryable()) {
            if (errorCode.equalsIgnoreCase(code)) {
                return false;
            }
        }
        return true;
    }

    private long backoffMs(MessageProperties.Retry r, int attempt) {
        List<Long> list = r.getBackoffMs();
        if (list == null || list.isEmpty()) {
            return 0L;
        }
        return list.get(Math.min(attempt, list.size() - 1));
    }

    private void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    public SenderRegistry getSenderRegistry() {
        return senderRegistry;
    }

    /**
     * 给批量任务用的窄接口：只投不发日志（批量自行汇总）
     */
    public List<Outcome> deliverAll(List<Message> messages) {
        List<Outcome> list = new ArrayList<>();
        if (messages == null) {
            return list;
        }
        for (Message m : messages) {
            list.add(deliver(m, true));
        }
        return list;
    }
}
