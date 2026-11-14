package com.zifang.z.msg.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zifang.z.msg.client.model.ZMsgSendRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * z-msg-client 自测：用 JDK 内置 HttpServer 打桩，验三件容易悄悄坏掉的事：
 * <ol>
 *   <li>AK 身份 ⇒ 请求头确实带 {@code X-Api-Key}，且不会把 AK 写进 body；</li>
 *   <li>平台 token 身份 ⇒ 请求头带 {@code Authorization: Bearer}；</li>
 *   <li>非 2xx / success=false ⇒ 抛 {@link ZMsgClientException}，且<b>异常里带着服务端那句人话</b>
 *       （"带 X-Api-Key 或平台登录态"这种话丢了，调用方就只能对着 401 猜）。</li>
 * </ol>
 */
public class ZMsgClientTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastBody = new AtomicReference<String>();
    private final AtomicReference<String> lastApiKey = new AtomicReference<String>();
    private final AtomicReference<String> lastAuth = new AtomicReference<String>();
    private final AtomicReference<String> lastPath = new AtomicReference<String>();
    private volatile int status = 200;
    private volatile String response = "{\"success\":true,\"data\":{\"msgId\":\"m1\"},\"code\":0,\"message\":\"success\"}";

    @BeforeEach
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws java.io.IOException {
        lastApiKey.set(exchange.getRequestHeaders().getFirst("X-Api-Key"));
        lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
        lastPath.set(exchange.getRequestURI().toString());
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        lastBody.set(new String(bos.toByteArray(), StandardCharsets.UTF_8));

        byte[] out = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    @Test
    public void akClientSendsApiKeyHeaderAndBodyWithoutSecret() {
        ZMsgClient client = ZMsgClientFactory.remote("http://127.0.0.1:" + port, "msga-test-key");
        Map<String, Object> data = client.send(
                ZMsgSendRequest.inApp(1001L, "标题", "正文"), null);

        assertNotNull(data);
        assertEquals("m1", data.get("msgId"));
        assertEquals("msga-test-key", lastApiKey.get(), "AK 身份必须带 X-Api-Key 头");
        assertTrue(lastBody.get().contains("\"userId\":1001"), "请求体应带 userId，实际 " + lastBody.get());
        assertTrue(lastBody.get().contains("标题"), "请求体应带 subject，实际 " + lastBody.get());
        assertTrue(!lastBody.get().contains("msga-test-key"), "AK 不能出现在 body 里，实际 " + lastBody.get());
        assertEquals("/api/msg/send", lastPath.get());
    }

    @Test
    public void tokenIdentitySendsBearerHeader() {
        ZMsgClient client = ZMsgClientFactory.remote("http://127.0.0.1:" + port);
        client.send(ZMsgSendRequest.inApp(1002L, "t", "c"), "user-token-xyz");

        assertEquals("Bearer user-token-xyz", lastAuth.get());
        assertTrue(lastApiKey.get() == null, "没配 AK 时不该有 X-Api-Key 头");
    }

    @Test
    public void unauthorizedKeepsServerMessage() {
        status = 401;
        response = "{\"success\":false,\"code\":401,\"message\":\"未识别调用者身份：带 X-Api-Key（客户端 AK）或平台登录态才能发消息\"}";

        ZMsgClient client = ZMsgClientFactory.remote("http://127.0.0.1:" + port);
        ZMsgClientException ex = assertThrows(ZMsgClientException.class,
                () -> client.send(ZMsgSendRequest.inApp(1003L, "t", "c"), null));

        assertEquals(401, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("X-Api-Key"),
                "异常里必须带着服务端那句人话，实际 " + ex.getMessage());
    }

    @Test
    public void successFalseAlsoThrows() {
        status = 200;
        response = "{\"success\":false,\"code\":500,\"message\":\"内部错误\"}";

        ZMsgClient client = ZMsgClientFactory.remote("http://127.0.0.1:" + port);
        ZMsgClientException ex = assertThrows(ZMsgClientException.class,
                () -> client.send(ZMsgSendRequest.inApp(1004L, "t", "c"), null));
        assertTrue(ex.getMessage().contains("内部错误"));
    }

    @Test
    public void channelsReturnsList() {
        response = "{\"success\":true,\"data\":[{\"channel\":\"IN_APP\",\"displayName\":\"站内信\"}],\"code\":0}";
        ZMsgClient client = ZMsgClientFactory.remote("http://127.0.0.1:" + port, "msga-x");
        List<Map<String, Object>> list = client.channels(null);

        assertEquals(1, list.size());
        assertEquals("IN_APP", list.get(0).get("channel"));
        assertEquals("/api/msg/channel/list", lastPath.get());
    }
}
