package com.zifang.z.msg.client;

import com.zifang.util.json.JsonUtil;
import com.zifang.z.msg.client.model.ZMsgSendRequest;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 远端 HTTP 实现（HttpURLConnection，零额外 HTTP 依赖，不依赖 Spring）。
 * <p>
 * 端点全部回 {@code Result{success, data, code, message}} 信封，这里统一取 {@code data}；
 * HTTP 非 2xx 或 {@code success=false} 一律抛 {@link ZMsgClientException}，
 * 让消费方的错误处理只有一个形状。
 * <p>
 * <b>写请求体用缓冲模式</b>（不调 {@code setFixedLengthStreamingMode}）：JDK 的
 * {@code HttpURLConnection} 拿到 401 会走"服务端要求认证 ⇒ 重发一次"的老逻辑，
 * streaming 模式下不能重发，于是抛 {@code HttpRetryException}，客户端连那个 401 正文都拿不到 ——
 * 而 401 正文里写着"带 X-Api-Key 或平台登录态"，恰恰是最该让调用方看到的那句。
 */
public class RemoteZMsgClient implements ZMsgClient {

    private final String baseUrl;
    /** AK 客户端的 access key；为空表示走平台 token 身份。 */
    private final String apiKey;
    private final int connectTimeoutMs = 5000;
    private final int readTimeoutMs = 30000;

    public RemoteZMsgClient(String baseUrl) {
        this(baseUrl, null);
    }

    public RemoteZMsgClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "";
        this.apiKey = apiKey == null || apiKey.trim().isEmpty() ? null : apiKey.trim();
    }

    @Override
    public Map<String, Object> send(ZMsgSendRequest request, String token) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return post(sendPath(), request.toMap(), token);
    }

    @Override
    public Map<String, Object> sendRaw(Map<String, Object> body, String token) {
        if (body == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return post(sendPath(), body, token);
    }

    /**
     * 发消息打哪个端点：<b>有 AK 走对外端点，没有 AK 走平台端点</b>。
     * <p>
     * 两者的差别是宿主的登录闸门：平台端点 {@code /api/msg/send} 在闸门内，
     * 要么带用户 cookie / Bearer token，要么带 AK（闸门放行后由 z-msg 自己判 AK）；
     * 对外端点 {@code /api/msg/open/send} 豁免了闸门（{@code @NoLogin}），
     * 所以它<b>只认 AK</b> —— 机器对机器没有用户身份，只能走这条。
     */
    private String sendPath() {
        return apiKey != null ? "/api/msg/open/send" : "/api/msg/send";
    }

    @Override
    public Map<String, Object> batchSubmit(Map<String, Object> body, String token) {
        if (body == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return post("/api/msg/batch/submit", body, token);
    }

    @Override
    public Map<String, Object> publish(String eventType, Long userId, Map<String, Object> params, String token) {
        StringBuilder path = new StringBuilder("/api/msg/publish?eventType=")
                .append(urlEncode(eventType));
        if (userId != null) {
            path.append("&userId=").append(userId);
        }
        return post(path.toString(), params == null ? Collections.<String, Object>emptyMap() : params, token);
    }

    @Override
    public Map<String, Object> clientInfo() {
        if (apiKey == null) {
            throw new IllegalStateException("clientInfo 需要客户端 AK："
                    + "用 ZMsgClientFactory.remote(baseUrl, apiKey) 建客户端");
        }
        Object data = call("GET", "/api/msg/open/client/me", null, null);
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) data;
            return m;
        }
        return Collections.emptyMap();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> channels(String token) {
        Object data = call("GET", "/api/msg/channel/list", token, null);
        if (data == null) {
            return Collections.emptyList();
        }
        if (data instanceof List) {
            List<Map<String, Object>> out = new java.util.ArrayList<Map<String, Object>>();
            for (Object item : (List<Object>) data) {
                if (item instanceof Map) {
                    out.add((Map<String, Object>) item);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }

    // ========== HTTP 工具 ==========

    private Map<String, Object> post(String path, Map<String, Object> body, String token) {
        Object data = call("POST", path, token, body);
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) data;
            return m;
        }
        return Collections.emptyMap();
    }

    private Object call(String method, String path, String token, Map<String, Object> body) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setRequestProperty("Accept", "application/json");
            if (apiKey != null) {
                conn.setRequestProperty("X-Api-Key", apiKey);
            }
            if (token != null && !token.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token.trim());
            }
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                byte[] payload = JsonUtil.toJson(body).getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
            }

            int status = conn.getResponseCode();
            String text = readBody(status >= 400 ? conn.getErrorStream() : conn.getInputStream());

            Map<String, Object> envelope;
            try {
                envelope = text == null || text.trim().isEmpty()
                        ? Collections.<String, Object>emptyMap()
                        : JsonUtil.parseToMap(text);
            } catch (Exception e) {
                throw new ZMsgClientException(method + " " + path + " 返回的不是 JSON："
                        + (text == null ? "" : text.substring(0, Math.min(300, text.length()))), e);
            }

            boolean success = Boolean.TRUE.equals(envelope.get("success"));
            if (status < 200 || status >= 300 || !success) {
                throw ZMsgClientException.of(method + " " + path + " 失败", status, envelope);
            }
            return toPlain(envelope.get("data"));
        } catch (ZMsgClientException e) {
            throw e;
        } catch (IOException e) {
            throw new ZMsgClientException(method + " " + path + " 调用失败：" + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * JsonUtil 解析出来的嵌套结构是 {@code JsonObject} / {@code JsonArray}，<b>不是</b>
     * {@code Map} / {@code List}（所以 {@code instanceof Map} 判定会失败，值悄悄变成 null）。
     * 这里统一转成普通 Map / List 再交给消费方。
     */
    @SuppressWarnings("unchecked")
    static Object toPlain(Object value) {
        if (value instanceof com.zifang.util.json.model.JsonObject) {
            com.zifang.util.json.model.JsonObject obj = (com.zifang.util.json.model.JsonObject) value;
            Map<String, Object> out = new java.util.LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> e : obj.getAllKeyValue()) {
                out.put(e.getKey(), toPlain(e.getValue()));
            }
            return out;
        }
        if (value instanceof com.zifang.util.json.model.JsonArray) {
            com.zifang.util.json.model.JsonArray arr = (com.zifang.util.json.model.JsonArray) value;
            List<Object> out = new java.util.ArrayList<Object>(arr.size());
            for (Object item : arr) {
                out.add(toPlain(item));
            }
            return out;
        }
        return value;
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = stream.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        stream.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String urlEncode(String text) {
        if (text == null) {
            return "";
        }
        try {
            return java.net.URLEncoder.encode(text, "UTF-8");
        } catch (IOException e) {
            return text;
        }
    }
}
