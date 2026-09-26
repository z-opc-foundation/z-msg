package com.zifang.z.msg.core.channel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * webhook provider 的两个契约：
 * <ol>
 *   <li><b>五条出口（成功 / 非 2xx / 连不上 / url 不合法 / receiver 为空）报的 provider
 *       必须就是 {@link WebhookSender#provider()}</b>。改之前成功分支写死 "webhook"、
 *       拒连分支写死 "http"，投递日志同一通道出现两个值，按 provider 聚合直接失真。</li>
 *   <li><b>只放行 http/https</b>。receiver 由业务给，{@code file://} 这类 handler
 *       会让"发一条 webhook"变成"读服务器本地文件"。</li>
 * </ol>
 * 每条负向断言都配了正向对照（同一个 sender 发一次真 HTTP 并真的被桩收到），
 * 否则"什么都发不出去"也能让它绿。
 */
class WebhookSenderTest {

    private static HttpServer server;
    private static String baseUrl;

    private static volatile int status = 200;
    private static volatile String lastBody;
    private static volatile int hits;

    @BeforeAll
    static void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                String reqBody = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
                byte[] out = "{\"accepted\":true}".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(status, out.length);
                OutputStream os = ex.getResponseBody();
                os.write(out);
                os.close();
                lastBody = reqBody;
                hits++;
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopStub() {
        if (server != null) {
            // 0 = 不等正在处理的请求，避免 JDK8 的 stop 卡在 preClose0
            server.stop(0);
        }
    }

    @AfterEach
    void resetStubState() {
        status = 200;
        lastBody = null;
        hits = 0;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[2048];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    @Test
    void successOutcomeReportsTheSenderProvider() {
        WebhookSender sender = new WebhookSender();
        MessageSendResult r = sender.send(baseUrl + "/hook", "{\"text\":\"hi\"}", 3000);

        assertTrue(r.isSuccess(), "正向对照必须真发出去: " + r.getErrorCode() + " " + r.getErrorMessage());
        assertEquals(sender.provider(), r.getProvider(),
                "成功分支的 provider 必须等于 sender.provider()，否则按 provider 统计会分裂成两个值");
        assertNotNull(r.getProviderMessageId());
        assertEquals(1, hits, "桩没收到请求，这条绿是假的");
        assertTrue("{\"text\":\"hi\"}".equals(lastBody), "body 没原样送到: " + lastBody);
    }

    @Test
    void everyFailureOutcomeReportsTheSameProvider() {
        WebhookSender sender = new WebhookSender();

        status = 500;
        MessageSendResult http500 = sender.send(baseUrl + "/hook", "{}", 3000);
        status = 200;

        MessageSendResult refused = sender.send("http://127.0.0.1:1/hook", "{}", 1500);
        MessageSendResult malformed = sender.send("not a url", "{}", 1500);
        MessageSendResult badScheme = sender.send("file:///etc/passwd", "{}", 1500);
        MessageSendResult noReceiver = sender.send(
                Message.builder().channel(Channels.WEBHOOK).subject("s").content("c").build());

        assertFalse(http500.isSuccess());
        assertFalse(refused.isSuccess());
        assertFalse(malformed.isSuccess());
        assertFalse(badScheme.isSuccess());
        assertFalse(noReceiver.isSuccess());

        // 每一支都要跑到自己那个错误码上：都归到同一个码，说明压根没分派到分支
        assertEquals("HTTP_500", http500.getErrorCode());
        assertEquals("WEBHOOK_ERROR", refused.getErrorCode());
        assertEquals("INVALID_URL", malformed.getErrorCode());
        assertEquals("UNSUPPORTED_SCHEME", badScheme.getErrorCode());
        assertEquals("INVALID_RECEIVER", noReceiver.getErrorCode());

        for (MessageSendResult r : new MessageSendResult[]{http500, refused, malformed, badScheme, noReceiver}) {
            assertEquals(sender.provider(), r.getProvider(),
                    r.getErrorCode() + " 这一支的 provider 和 sender.provider() 不一致");
        }
        assertEquals(1, hits, "只有 500 那一支该打到桩上");
    }

    @Test
    void nonHttpSchemeNeverOpensAConnection() {
        WebhookSender sender = new WebhookSender();

        MessageSendResult r = sender.send("file://" + baseUrl + "/hook", "{}", 1500);

        assertFalse(r.isSuccess());
        assertEquals("UNSUPPORTED_SCHEME", r.getErrorCode());
        assertEquals(0, hits, "非 http scheme 却打到了桩上：闸门形同虚设");
        // 同一个 sender 发真 http 必须成功，否则上面的断言只是"什么都发不出去"
        assertTrue(sender.send(baseUrl + "/hook", "{}", 3000).isSuccess(),
                "正向对照：http 通道本身是通的");
    }
}
