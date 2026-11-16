package com.zifang.z.msg.web;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 测试里发 POST 的小工具：<b>不</b>用 Spring 的 {@code SimpleClientHttpRequestFactory}。
 * <p>
 * 原因：它写请求体时用 fixed-length streaming，JDK 的 {@code HttpURLConnection} 一旦拿到
 * <b>401</b> 就会走"服务端要求认证 ⇒ 重发一次"的老逻辑，而 streaming 模式下不能重发，
 * 于是抛 {@code HttpRetryException: cannot retry due to server authentication, in streaming mode} ——
 * 客户端根本没拿到那个 401 响应体（我们的 401 正文里写着"带 X-Api-Key 或平台登录态"，
 * 恰恰是最该让调用方看到的那句）。
 * <p>
 * 所以这里用默认的缓冲模式写 body：JDK 不进 streaming 分支，401 正常返回，
 * 错误流自己读。这也更接近真实调用方（curl / axios / fetch）的行为。
 */
final class MsgSendHttp {

    private MsgSendHttp() {
    }

    static Reply post(int port, String path, String body, String apiKey) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(
                "http://localhost:" + port + path).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (apiKey != null) {
            conn.setRequestProperty("X-Api-Key", apiKey);
        }
        byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }
        return finish(conn);
    }

    /**
     * GET 版（同 {@link #post} 的理由：不走 Spring 的 streaming 写 body，401 拿得到正文）。
     */
    static Reply get(int port, String path, String apiKey) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(
                "http://localhost:" + port + path).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        if (apiKey != null) {
            conn.setRequestProperty("X-Api-Key", apiKey);
        }
        return finish(conn);
    }

    private static Reply finish(HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode();
        InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String text = "";
        if (stream != null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = stream.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            text = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            stream.close();
        }
        return new Reply(status, text);
    }

    /**
     * 一次请求的结果：状态码 + 正文。
     */
    static final class Reply {
        final int status;
        final String body;

        Reply(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
