package com.zifang.z.msg.channels.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地 HTTP stub（JDK 自带 HttpServer，不引 WireMock）。
 * <p>
 * 把收到的每个请求原文（method/path/query/headers/body）记进内存，响应按 path 队列弹出
 * （队列空则用默认响应），可挂一个有界 sleep 模拟慢服务端 —— 超时测试用它。
 * 测试断言一律以这里的"收到条数/原文"为准，不数客户端自己记的数。
 */
public final class StubServer implements AutoCloseable {

    public static final class Recorded {
        public final String method;
        public final String path;
        public final String rawQuery;
        public final Map<String, String> headers;
        public final String body;

        Recorded(String method, String path, String rawQuery, Map<String, String> headers, String body) {
            this.method = method;
            this.path = path;
            this.rawQuery = rawQuery;
            this.headers = headers;
            this.body = body;
        }

        /** query 参数解码后的 map（重复 key 取最后一个）。 */
        public Map<String, String> query() {
            Map<String, String> out = new LinkedHashMap<>();
            if (rawQuery == null || rawQuery.isEmpty()) {
                return out;
            }
            for (String pair : rawQuery.split("&")) {
                int eq = pair.indexOf('=');
                if (eq < 0) {
                    out.put(decode(pair), "");
                } else {
                    out.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
                }
            }
            return out;
        }

        private static String decode(String s) {
            try {
                return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                throw new IllegalStateException("query 解码失败: " + s, e);
            }
        }

        public String header(String name) {
            return headers.get(name.toLowerCase());
        }
    }

    private static final class Resp {
        final int status;
        final String body;
        final long sleepMillis;

        Resp(int status, String body, long sleepMillis) {
            this.status = status;
            this.body = body;
            this.sleepMillis = sleepMillis;
        }
    }

    private final HttpServer server;
    private final List<Recorded> requests = Collections.synchronizedList(new ArrayList<Recorded>());
    private final Map<String, List<Resp>> queues = new LinkedHashMap<>();
    private Resp fallback = new Resp(200, "{\"errcode\":0,\"errmsg\":\"ok\"}", 0);

    public StubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 队列为空时的默认响应。 */
    public StubServer defaultResponse(int status, String body) {
        this.fallback = new Resp(status, body, 0);
        return this;
    }

    public StubServer enqueue(String path, int status, String body) {
        return enqueue(path, status, body, 0);
    }

    /** 入队一个响应；sleepMillis 是给 stub 的有界延时（模拟慢服务端），非测试轮询。 */
    public synchronized StubServer enqueue(String path, int status, String body, long sleepMillis) {
        queues.computeIfAbsent(path, k -> new ArrayList<Resp>())
                .add(new Resp(status, body, Math.min(sleepMillis, 2000)));
        return this;
    }

    public synchronized int countFor(String path) {
        int n = 0;
        synchronized (requests) {
            for (Recorded r : requests) {
                if (r.path.equals(path)) {
                    n++;
                }
            }
        }
        return n;
    }

    public Recorded last(String path) {
        Recorded found = null;
        synchronized (requests) {
            for (Recorded r : requests) {
                if (r.path.equals(path)) {
                    found = r;
                }
            }
        }
        if (found == null) {
            throw new AssertionError("stub 未收到 path=" + path + " 的请求（共 " + requests.size() + " 条）");
        }
        return found;
    }

    public List<Recorded> all() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    public void clear() {
        requests.clear();
        synchronized (queues) {
            queues.clear();
        }
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String rawQuery = ex.getRequestURI().getRawQuery();
        String body = readAll(ex.getRequestBody());
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> h : ex.getRequestHeaders().entrySet()) {
            headers.put(h.getKey().toLowerCase(), h.getValue().isEmpty() ? "" : h.getValue().get(0));
        }
        requests.add(new Recorded(ex.getRequestMethod(), path, rawQuery, headers, body));

        Resp resp;
        synchronized (queues) {
            List<Resp> q = queues.get(path);
            if (q != null && !q.isEmpty()) {
                resp = q.remove(0);
            } else {
                resp = fallback;
            }
        }
        if (resp.sleepMillis > 0) {
            try {
                Thread.sleep(resp.sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] out = resp.body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(resp.status, out.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
