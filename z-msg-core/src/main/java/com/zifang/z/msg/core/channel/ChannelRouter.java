package com.zifang.z.msg.core.channel;

import com.zifang.util.core.lang.RandomUtil;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.core.domain.entity.MsgDeliveryLogDO;
import com.zifang.z.msg.core.domain.mapper.MsgDeliveryLogMapper;
import com.zifang.z.msg.core.domain.service.MsgUserPreferenceService;
import com.zifang.z.msg.core.ratelimit.ChannelRateLimiter;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 渠道路由器 (Phase 2/3 核心入口)
 * <p>
 * 给定一个事件 (bizType + receiver + params),自动决定:
 * 1. 哪些渠道被允许 (用户偏好 + 静默时段)
 * 2. 各渠道是否被限流
 * 3. 渲染各渠道的 subject/content
 * 4. 调用对应 Sender
 * 5. 落投递日志
 * <p>
 * 设计要点:
 * - 同一事件可同时投递多个渠道 (fan-out)
 * - 用户偏好 > 默认配置
 * - 限流被拒后写入日志 status=2 (failed, ERR_RATE_LIMIT)
 */
@Component
public class ChannelRouter {

    private static final Logger log = LogManager.getLogger(ChannelRouter.class);
    /**
     * 限流器:每通道 50 qps 默认
     */
    private final ChannelRateLimiter rateLimiter = new ChannelRateLimiter(50);
    @Resource
    private MessageTemplateEngine templateEngine;
    @Resource
    private MsgUserPreferenceService preferenceService;
    @Resource
    private MsgDeliveryLogMapper deliveryLogMapper;
    @Resource
    private WebhookSender webhookSender;
    @Resource
    private ImSender imSender;
    @Resource
    private PushSender pushSender;
    @Resource
    private DefaultMessageGateway defaultMessageGateway;

    /**
     * fan-out 主入口:一个事件 → 多个渠道
     *
     * @param bizType   业务类型
     * @param userId    接收用户
     * @param receivers 各渠道的接收方 {"SMS":"138...","EMAIL":"a@b.com","WEBHOOK":"https://..."}
     * @param params    模板渲染参数
     * @param locale    Phase 3 多语言
     * @return 每个渠道的发送结果
     */
    public List<SendOutcome> fanOut(String bizType, Long userId,
                                    Map<String, String> receivers,
                                    Map<String, String> params,
                                    String locale) {
        List<SendOutcome> outcomes = new ArrayList<>();
        if (receivers == null || receivers.isEmpty()) {
            return outcomes;
        }

        // 1. 用户偏好过滤
        Set<String> allowed = preferenceService.getAllowedChannels(userId, bizType);
        boolean inQuietHours = preferenceService.isInQuietHours(userId, bizType);

        for (Map.Entry<String, String> e : receivers.entrySet()) {
            String channel = e.getKey();
            String receiver = e.getValue();

            if (allowed != null && !allowed.contains(channel.toUpperCase())) {
                outcomes.add(new SendOutcome(channel, false, "PREF_BLOCKED", "用户偏好屏蔽"));
                logDelivery(bizType, channel, receiver, userId, null, null, null, 2, "PREF_BLOCKED", "用户偏好屏蔽", null);
                continue;
            }
            if (inQuietHours && !isUrgent(bizType)) {
                outcomes.add(new SendOutcome(channel, false, "QUIET_HOURS", "静默时段"));
                logDelivery(bizType, channel, receiver, userId, null, null, null, 2, "QUIET_HOURS", "静默时段", null);
                continue;
            }

            // 2. 限流
            if (!rateLimiter.tryAcquire(channel)) {
                outcomes.add(new SendOutcome(channel, false, "RATE_LIMIT", "通道限流"));
                logDelivery(bizType, channel, receiver, userId, null, null, null, 2, "RATE_LIMIT", "通道限流", null);
                continue;
            }

            // 3. 渲染 + 发送 (含指数退避重试)
            MessageTemplateEngine.RenderedTemplate rendered = templateEngine.renderWithLocale(bizType, channel, locale, params);
            MessageSendResult res = dispatchWithRetry(channel, receiver, rendered, params);

            // 4. 落日志
            logDelivery(bizType, channel, receiver, userId, null,
                    rendered.getSubject(), rendered.getContent(),
                    res.isSuccess() ? 1 : 2,
                    res.getErrorCode(), res.getErrorMessage(),
                    res.getProviderMessageId());

            outcomes.add(new SendOutcome(channel, res.isSuccess(), res.getErrorCode(), res.getErrorMessage()));
        }
        return outcomes;
    }

    /**
     * 指数退避重试：失败后最多重试 maxRetry 次，每次间隔 2^attempt 秒
     */
    private MessageSendResult dispatchWithRetry(String channel, String receiver,
                                                MessageTemplateEngine.RenderedTemplate rendered,
                                                Map<String, String> params) {
        int maxRetry = 3;
        MessageSendResult res = null;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            res = dispatch(channel, receiver, rendered, params);
            if (res.isSuccess() || attempt == maxRetry) {
                break;
            }
            long delayMs = (long) Math.pow(2, attempt) * 1000;
            log.warn("[ChannelRouter] dispatch failed attempt={}/{} channel={} receiver={} retryIn={}ms err={}",
                    attempt + 1, maxRetry, channel, receiver, delayMs, res.getErrorMessage());
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return res;
    }

    private MessageSendResult dispatch(String channel, String receiver,
                                       MessageTemplateEngine.RenderedTemplate rendered,
                                       Map<String, String> params) {
        long start = System.currentTimeMillis();
        try {
            switch (channel.toUpperCase()) {
                case Channels.SMS:
                    return defaultMessageGateway.sendSms(
                            com.zifang.z.msg.api.SmsMessage.builder()
                                    .phone(receiver)
                                    .bizType(params.getOrDefault("bizType", ""))
                                    .templateParams(params)
                                    .build());
                case Channels.EMAIL:
                    return defaultMessageGateway.sendEmail(
                            com.zifang.z.msg.api.EmailMessage.builder()
                                    .to(receiver)
                                    .bizType(params.getOrDefault("bizType", ""))
                                    .templateParams(params)
                                    .subject(rendered.getSubject())
                                    .build());
                case Channels.WEBHOOK:
                    String payload = "{\"subject\":\"" + rendered.getSubject()
                            + "\",\"content\":\"" + rendered.getContent() + "\"}";
                    return webhookSender.send(receiver, payload, 5000);
                case Channels.IM_WECOM:
                case Channels.IM_DINGTALK:
                case Channels.IM_FEISHU:
                    return imSender.sendByChannel(channel, receiver, rendered.getContent());
                case Channels.PUSH_FCM:
                case Channels.PUSH_APNS:
                case Channels.PUSH_WEB:
                    return pushSender.sendByChannel(channel, receiver,
                            rendered.getSubject(), rendered.getContent());
                default:
                    return MessageSendResult.fail("unknown", "UNSUPPORTED_CHANNEL",
                            "不支持的渠道: " + channel);
            }
        } catch (Exception e) {
            log.error("[ChannelRouter] dispatch exception channel={} err={}", channel, e.getMessage());
            return MessageSendResult.fail("router", "DISPATCH_ERROR", e.getMessage());
        }
    }

    private boolean isUrgent(String bizType) {
        // 验证码、密码重置视为紧急,静默时段也发
        return "REGISTER".equals(bizType) || "LOGIN".equals(bizType)
                || "RESET_PWD".equals(bizType) || "SECURITY_ALERT".equals(bizType);
    }

    private void logDelivery(String bizType, String channel, String receiver, Long userId,
                             Long templateId, String subject, String content,
                             int status, String errorCode, String errorMessage) {
        logDelivery(bizType, channel, receiver, userId, templateId, subject, content,
                status, errorCode, errorMessage, null);
    }

    private void logDelivery(String bizType, String channel, String receiver, Long userId,
                             Long templateId, String subject, String content,
                             int status, String errorCode, String errorMessage,
                             String providerMessageId) {
        try {
            MsgDeliveryLogDO log = new MsgDeliveryLogDO();
            log.setBizType(bizType);
            log.setChannel(channel);
            log.setReceiver(receiver);
            log.setUserId(userId);
            log.setTemplateId(templateId);
            log.setRenderedSubject(subject);
            log.setRenderedContent(content);
            log.setStatus(status);
            log.setErrorCode(errorCode);
            log.setErrorMessage(errorMessage);
            log.setProviderMessageId(providerMessageId != null ? providerMessageId : RandomUtil.uuidShort(16));
            log.setRetryCount(0);
            log.setMaxRetry(3);
            log.setCreatedTime(LocalDateTime.now());
            log.setUpdatedTime(LocalDateTime.now());
            deliveryLogMapper.insert(log);
        } catch (Exception ex) {
            ChannelRouter.log.error("[ChannelRouter] logDelivery failed: {}", ex.getMessage());
        }
    }

    public ChannelRateLimiter getRateLimiter() {
        return rateLimiter;
    }

    public static class SendOutcome {
        private final String channel;
        private final boolean success;
        private final String errorCode;
        private final String errorMessage;

        public SendOutcome(String channel, boolean success, String errorCode, String errorMessage) {
            this.channel = channel;
            this.success = success;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public String getChannel() {
            return channel;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
