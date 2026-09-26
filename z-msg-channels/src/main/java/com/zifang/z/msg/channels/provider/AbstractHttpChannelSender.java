package com.zifang.z.msg.channels.provider;

import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.channels.config.ChannelsProperties;
import com.zifang.z.msg.channels.http.HttpResponse;
import com.zifang.z.msg.channels.http.SimpleHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * provider 公共层：配置取用、超时/重试执行、消息文本组装、供应商 JSON 错误码翻译。
 * <p>
 * 两条硬规矩在这里统一兑现，子类不需要各自 remember：
 * <ul>
 *   <li>{@link #send(Message)} 的模板方法把子类逻辑整个包在 try/catch 里 ——
 *       编程错误以外的任何异常都会变成 {@code MessageSendResult.fail}（ChannelSender 契约）；</li>
 *   <li>日志只输出 channel/provider/msgId/receiver 与"去 query 的 URL"（{@code safeTarget}），
 *       凭据永远不进日志。</li>
 * </ul>
 */
public abstract class AbstractHttpChannelSender implements ChannelSender {

    protected final Logger log = LogManager.getLogger(getClass());

    protected final ChannelsProperties properties;
    protected final SimpleHttpClient http;
    /**
     * {@code Channels} 常量（也是 z-msg.channel.&lt;key&gt; 的归一化 key）
     */
    protected final String channel;
    private final String providerName;
    /**
     * cfg.mock=true 时接管发送（本地开发/联调：不改 provider 选择就能停掉真实外发）
     */
    private final RecordingMockSender recorder;

    protected AbstractHttpChannelSender(ChannelsProperties properties, SimpleHttpClient http,
                                        String channel, String providerName) {
        this.properties = properties;
        this.http = http;
        this.channel = channel;
        this.providerName = providerName;
        this.recorder = new RecordingMockSender(channel);
    }

    @Override
    public final String channel() {
        return channel;
    }

    @Override
    public final String provider() {
        return providerName;
    }

    /**
     * {@code z-msg.channel.<ch>.mock=true} 时本 bean 变为录制 mock —— 契约要求 mock 必须
     * {@code isMock()=true}（投递日志记 status=3），这里跟着同一个配置翻转，二者不会说谎。
     */
    @Override
    public boolean isMock() {
        return cfg().isMock();
    }

    /**
     * mock 模式下不需要凭据也算 ready（否则 router 在 send 之前就 skip 掉了）；
     * 真实模式由子类 {@link #configured()} 回答。
     */
    @Override
    public final boolean ready() {
        return cfg().isMock() || configured();
    }

    /** 真实发送所需的凭据是否齐备。 */
    protected abstract boolean configured();

    /** 本实例录制到的请求（mock=true 时可直接取；宿主也能 getBean 后调用）。 */
    public java.util.List<RecordingMockSender.Recorded> recorded() {
        return recorder.recorded();
    }

    public void clearRecorded() {
        recorder.clear();
    }

    protected ChannelsProperties.ChannelCfg cfg() {
        return properties.channel(channel);
    }

    @Override
    public final MessageSendResult send(Message message) {
        if (message == null) {
            return MessageSendResult.fail(providerName, "INVALID_MESSAGE", "message 为 null");
        }
        if (cfg().isMock()) {
            return recorder.send(message);
        }
        try {
            return doSend(message);
        } catch (Exception e) {
            // 兜底：ChannelSender 约定业务失败不得抛异常；到这里的是没预料到的运行时异常，
            // 同样翻成 fail，让 router 的重试/降级拿到结构化结果。
            log.error("[z-msg-{}] 发送异常 channel={} msgId={} err={}",
                    providerName, channel, message.getMsgId(), e.toString());
            return MessageSendResult.fail(providerName, "PROVIDER_EXCEPTION", e.getMessage());
        }
    }

    protected abstract MessageSendResult doSend(Message message);

    /**
     * 带 HTTP 层重试的执行：仅传输错误/5xx 重试，次数 = cfg.retries（封顶 3）。
     * 供应商业务拒绝（2xx + errcode!=0）不在这层重试，那是降级链的事。
     */
    protected HttpResponse execute(String method, String url, Map<String, String> headers, String body) {
        ChannelsProperties.ChannelCfg cfg = cfg();
        int connect = cfg.connectTimeoutMsOrDefault();
        int read = cfg.readTimeoutMsOrDefault();
        int attempts = 1 + cfg.retriesCapped();
        HttpResponse last = null;
        for (int i = 0; i < attempts; i++) {
            last = "GET".equals(method)
                    ? http.get(url, headers, connect, read)
                    : http.post(url, headers, body, connect, read);
            if (!last.isRetryable() || i == attempts - 1) {
                return last;
            }
            log.warn("[z-msg-{}] HTTP 重试 {}/{} target={} err={}",
                    providerName, i + 1, attempts, SimpleHttpClient.safeTarget(url),
                    last.isTransportError() ? last.getErrorCode() : "HTTP_" + last.getStatus());
        }
        return last;
    }

    protected HttpResponse postJson(String url, String json, Map<String, String> extraHeaders) {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("Content-Type", "application/json; charset=UTF-8");
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }
        return execute("POST", url, headers, json);
    }

    protected HttpResponse get(String url, Map<String, String> extraHeaders) {
        return execute("GET", url, extraHeaders, null);
    }

    /**
     * 机器人渠道的正文：subject 非空时作为首行（钉钉/企微的"关键词"安全设置通常要求
     * 正文含业务前缀），否则只发 content。两者皆空返回 null 由调用方 fail。
     */
    protected String robotText(Message message) {
        String subject = trimToNull(message.getSubject());
        String content = trimToNull(message.getContent());
        if (subject == null && content == null) {
            // 退一步：模板参数拼不平铺；IM 机器人没有"参数"概念，直接让调用方报 INVALID_CONTENT
            return null;
        }
        if (subject == null) {
            return content;
        }
        return content == null ? subject : subject + "\n" + content;
    }

    /** 传输失败 / 非 2xx 的统一 fail 翻译。 */
    protected MessageSendResult failFromHttp(HttpResponse resp) {
        if (resp.isTransportError()) {
            return MessageSendResult.fail(providerName, resp.getErrorCode(), resp.getErrorMessage());
        }
        return MessageSendResult.fail(providerName, "HTTP_" + resp.getStatus(),
                "HTTP " + resp.getStatus() + " body=" + abbreviate(resp.getBody()));
    }

    protected static String trimToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    protected static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }

    /** base-url 拼接：cfg.url 给了全量就用它，否则 baseUrl + path（去掉 baseUrl 尾部斜杠）。 */
    protected String buildUrl(String urlOverride, String defaultBaseUrl, String path) {
        String full = trimToNull(urlOverride);
        if (full != null) {
            return full;
        }
        String base = trimToNull(cfg().getBaseUrl());
        if (base == null) {
            base = defaultBaseUrl;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }
}
