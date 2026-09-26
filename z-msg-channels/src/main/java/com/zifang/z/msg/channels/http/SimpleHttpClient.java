package com.zifang.z.msg.channels.http;

import com.zifang.z.msg.channels.config.ChannelsProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 极简共享 HTTP 客户端（JDK {@link HttpURLConnection}，不引第三方 client）。
 * <p>
 * 存在意义：把六家 provider 的"POST JSON / GET、超时、有限读响应、异常吞掉"收敛到一处，
 * 并钉死三条规矩：
 * <ol>
 *   <li>超时来自配置且夹到上限（{@link ChannelsProperties#MAX_CONNECT_TIMEOUT_MS} /
 *       {@link ChannelsProperties#MAX_READ_TIMEOUT_MS}），"配 0 当无限等"在这里就走不通；</li>
 *   <li>响应体最多读 {@link ChannelsProperties#MAX_RESPONSE_BYTES} 字节，多出的丢弃；</li>
 *   <li>任何 IOException（含超时）都翻成 {@link HttpResponse#error} —— 本类不外抛，
 *       满足 ChannelSender "供应商侧失败不得抛异常" 的契约源头。</li>
 * </ol>
 */
public class SimpleHttpClient {

    private static final Logger log = LogManager.getLogger(SimpleHttpClient.class);

    public HttpResponse get(String url, Map<String, String> headers, int connectTimeoutMs, int readTimeoutMs) {
        return exchange("GET", url, headers, null, connectTimeoutMs, readTimeoutMs);
    }

    /**
     * POST 一个已序列化好的 body（通常是 JSON）。Content-Type 由 headers 给出。
     */
    public HttpResponse post(String url, Map<String, String> headers, String body,
                             int connectTimeoutMs, int readTimeoutMs) {
        byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        return exchange("POST", url, headers, payload, connectTimeoutMs, readTimeoutMs);
    }

    private HttpResponse exchange(String method, String url, Map<String, String> headers,
                                  byte[] body, int connectTimeoutMs, int readTimeoutMs) {
        int connect = clamp(connectTimeoutMs, ChannelsProperties.DEFAULT_CONNECT_TIMEOUT_MS,
                ChannelsProperties.MAX_CONNECT_TIMEOUT_MS);
        int read = clamp(readTimeoutMs, ChannelsProperties.DEFAULT_READ_TIMEOUT_MS,
                ChannelsProperties.MAX_READ_TIMEOUT_MS);
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            String scheme = u.getProtocol();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return HttpResponse.error("HTTP_UNSUPPORTED_SCHEME", "只允许 http/https: " + scheme);
            }
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(connect);
            conn.setReadTimeout(read);
            conn.setInstanceFollowRedirects(false);
            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    conn.setRequestProperty(h.getKey(), h.getValue());
                }
            }
            if (body != null) {
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(body.length);
                OutputStream os = conn.getOutputStream();
                try {
                    os.write(body);
                    os.flush();
                } finally {
                    closeQuietly(os);
                }
            }
            int status = conn.getResponseCode();
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String respBody = readLimited(in);
            if (in != null) {
                closeQuietly(in);
            }
            if (status >= 400) {
                // 非 2xx 也留一行日志：排障时"provider 为什么 fail"至少能看到命中了哪个端点；
                // 只记 host+path，响应体/ query（可能带凭据或用户数据）不落日志。
                log.warn("[z-msg-http] 非 2xx method={} target={} status={}", method, safeTarget(url), status);
            }
            return HttpResponse.of(status, respBody);
        } catch (SocketTimeoutException e) {
            // 日志只记 host+path，绝不记 query —— 各家凭据都在 query 里
            log.warn("[z-msg-http] 超时 method={} target={} err={}", method, safeTarget(url), e.toString());
            return HttpResponse.error("HTTP_TIMEOUT", "HTTP " + method + " 超时: " + safeTarget(url));
        } catch (IOException e) {
            log.warn("[z-msg-http] 传输失败 method={} target={} err={}", method, safeTarget(url), e.toString());
            return HttpResponse.error("HTTP_TRANSPORT_ERROR", e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (Exception e) {
            log.warn("[z-msg-http] 未知异常 method={} target={} err={}", method, safeTarget(url), e.toString());
            return HttpResponse.error("HTTP_ERROR", e.toString());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 有限读取：最多 MAX_RESPONSE_BYTES 字节，剩余部分丢弃不读，防被对端灌爆内存。 */
    private String readLimited(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream(1024);
        byte[] chunk = new byte[4096];
        int total = 0;
        int n;
        while (total < ChannelsProperties.MAX_RESPONSE_BYTES
                && (n = in.read(chunk, 0, Math.min(chunk.length, ChannelsProperties.MAX_RESPONSE_BYTES - total))) > 0) {
            buf.write(chunk, 0, n);
            total += n;
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    /** host + path（去掉 query 和 userInfo），仅用于日志。全模块唯一的"可打日志的 URL 形态"。 */
    public static String safeTarget(String url) {
        try {
            URL u = new URL(url);
            String path = u.getPath() == null ? "" : u.getPath();
            return u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "") + path;
        } catch (Exception e) {
            return "<bad-url>";
        }
    }

    private static int clamp(int v, int fallback, int max) {
        if (v <= 0) {
            return fallback;
        }
        return Math.min(v, max);
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                // 关闭失败不影响已拿到的结果
            }
        }
    }
}
