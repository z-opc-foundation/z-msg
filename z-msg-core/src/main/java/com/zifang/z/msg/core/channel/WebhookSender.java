package com.zifang.z.msg.core.channel;

import com.zifang.util.core.lang.RandomUtil;
import com.zifang.z.msg.api.MessageSendResult;
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
 */
@Component
public class WebhookSender {

    private static final Logger log = LogManager.getLogger(WebhookSender.class);

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
