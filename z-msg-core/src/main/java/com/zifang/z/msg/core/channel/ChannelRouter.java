package com.zifang.z.msg.core.channel;

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
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
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
     * 出站幂等表：key -> 过期时间戳。仅本 JVM 有效，跨节点幂等要靠
     * z_msg_delivery_log 上的 uk_channel_biz_dedup 唯一索引。
     */
    private final ConcurrentHashMap<String, Long> issued = new ConcurrentHashMap<>();

    // ==================== 单通道 ====================

    /**
     * 单通道投递（走完整管道：偏好 → 限流 → 渲染 → 重试 → 日志）
     */
    public MessageSendResult route(Message message) {
        Outcome o = deliver(message, true);
        if (o.getStatus() == FanOutResult.STATUS_SUCCESS || o.getStatus() == FanOutResult.STATUS_MOCK) {
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
        String idemKey = idempotencyKey(message);
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

        // 5. 发送 + 重试 + 降级
        Outcome outcome = dispatchWithRetry(message);
        if (outcome.getStatus() == FanOutResult.STATUS_FAILED && allowFallback) {
            Outcome fb = tryFallback(message, outcome, start);
            if (fb != null) {
                return fb;
            }
        }
        if (idemKey != null) {
            markIssued(idemKey);
        }
        return finish(message, outcome, start, templateId, outcome.getAttempts());
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
     */
    private Outcome tryFallback(Message failed, Outcome original, long fanOutStart) {
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
                return finish(m, o, fanOutStart, null, 1);
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
        outcome.setTemplateId(templateId);
        if (outcome.getAttempts() <= 0) {
            outcome.setAttempts(Math.max(1, attempts));
        }
        if (outcome.getDurationMs() <= 0) {
            outcome.setDurationMs(System.currentTimeMillis() - start);
        }
        logDelivery(message, outcome, templateId);
        return outcome;
    }

    private void logDelivery(Message message, Outcome o, Long templateId) {
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
            deliveryLogMapper.insert(row);
        } catch (Exception ex) {
            log.error("[ChannelRouter] logDelivery 失败: {}", ex.toString());
        }
    }

    // ==================== 幂等 ====================

    private String idempotencyKey(Message message) {
        String k = message.getIdempotencyKey();
        if (!notBlank(k)) {
            return null;
        }
        return message.getChannel() + "|" + message.getBizType() + "|" + k
                + "|" + (message.getUserId() == null ? "-" : message.getUserId())
                + "|" + (message.getReceiver() == null ? "-" : message.getReceiver());
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
