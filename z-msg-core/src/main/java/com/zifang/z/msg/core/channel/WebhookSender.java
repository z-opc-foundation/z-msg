package com.zifang.z.msg.core.channel;

import com.zifang.util.core.lang.RandomUtil;
import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.core.json.MsgJson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Webhook 通道 (Phase 2.1)
 * <p>
 * 通过 HTTP POST 回调外部 URL,支持失败重试。
 * 默认无外部 HTTP client 依赖,使用 JDK HttpURLConnection。
 * <p>
 * 1.1.0: 它同时是 {@code Channels.WEBHOOK} 的 {@link ChannelSender} provider，
 * 因此可以出现在 {@code z-msg.channel.webhook.fallback-channels} 降级链里
 * （1.0.0 只能被 ChannelRouter 的 switch 硬编码调用，业务无法直接经网关发 webhook）。
 */
@Component
public class WebhookSender implements ChannelSender {

    private static final Logger log = LogManager.getLogger(WebhookSender.class);

    @Override
    public String channel() {
        return Channels.WEBHOOK;
    }

    @Override
    public String provider() {
        return "http";
    }

    @Override
    public boolean ready() {
        // 无全局必填配置：url 逐条消息自带
        return true;
    }

    @Override
    public MessageSendResult send(Message message) {
        String url = message.getReceiver();
        if (url == null || url.trim().isEmpty()) {
            return MessageSendResult.fail("http", "INVALID_RECEIVER", "webhook url 为空");
        }
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("msgId", message.getMsgId());
        body.put("bizType", message.getBizType());
        body.put("subject", message.getSubject());
        body.put("content", message.getContent());
        body.put("linkUrl", message.getLinkUrl());
        body.put("userId", message.getUserId());
        body.put("params", message.getParams());
        Object override = message.getVendorOptions().get("payload");
        String payload = override == null ? MsgJson.toJson(body) : String.valueOf(override);
        int timeout = message.getVendorOptions().get("timeoutMs") instanceof Number
                ? ((Number) message.getVendorOptions().get("timeoutMs")).intValue() : 5000;
        return send(url, payload, timeout);
    }

    public MessageSendResult send(String url, String payloadJson, int timeoutMs) {
        long start = System.currentTimeMillis();
        String providerMessageId = "WEBHOOK-" + RandomUtil.uuidShort(16);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("X-Msg-Provider-Id", providerMessageId);
            byte[] body = payloadJson.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            conn.getOutputStream().write(body);
            int code = conn.getResponseCode();
            int duration = (int) (System.currentTimeMillis() - start);
            if (code >= 200 && code < 300) {
                log.info("[Webhook] OK url={} code={} duration={}ms id={}", url, code, duration, providerMessageId);
                return MessageSendResult.ok("webhook", providerMessageId);
            } else {
                log.warn("[Webhook] FAIL url={} code={} duration={}ms id={}", url, code, duration, providerMessageId);
                return MessageSendResult.fail("webhook",
                        "HTTP_" + code, "Webhook 返回非 2xx: " + code);
            }
        } catch (Exception e) {
            log.error("[Webhook] EXCEPTION url={} err={}", url, e.getMessage());
            return MessageSendResult.fail("webhook", "WEBHOOK_ERROR", e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }

        }
    }
}
