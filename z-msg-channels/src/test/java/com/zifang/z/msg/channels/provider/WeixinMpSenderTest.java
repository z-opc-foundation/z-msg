package com.zifang.z.msg.channels.provider;

import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.channels.config.ChannelsProperties;
import com.zifang.z.msg.channels.http.SimpleHttpClient;
import com.zifang.z.msg.channels.support.StubServer;
import com.zifang.z.msg.core.json.MsgJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 微信公众号模板消息：access_token 按 appId 缓存（N 条消息只取 1 次），
 * errcode=40001/42001 作废缓存重取一次并重发一次。
 * <p>
 * 所有计数都数 stub 侧收到的请求条数，不信客户端自己记的数。
 */
class WeixinMpSenderTest {

    private static final String APP_ID = "wx8a9b0c1d2e3f4a5b";
    private static final String APP_SECRET = "wx-app-secret-VALUE";
    private static final String TEMPLATE_ID = "TPL-777";

    private StubServer stub;
    private ChannelsProperties props;
    private WeixinMpSender sender;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubServer();
        props = new ChannelsProperties();
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        cfg.setAppId(APP_ID);
        cfg.setAppSecret(APP_SECRET);
        cfg.setTemplateCode(TEMPLATE_ID);
        props.put(Channels.IM_WEIXIN_MP, cfg);
        sender = new WeixinMpSender(props, new SimpleHttpClient());
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static Message msg(int i) {
        return Message.builder().channel(Channels.IM_WEIXIN_MP)
                .receiver("OPENID-user-" + i)
                .bizType("ORDER_PAID")
                .linkUrl("https://shop.example.com/order/" + i)
                .param("character_string1", "ORD" + i)
                .param("amount3", "99.00")
                .build();
    }

    private void enqueueOkSend() {
        stub.enqueue("/cgi-bin/message/template/send", 200, "{\"errcode\":0,\"errmsg\":\"ok\",\"msgid\":9527}");
    }

    @Test
    void tokenUrlAndSendBodyShape() {
        stub.enqueue("/cgi-bin/token", 200, "{\"access_token\":\"tok-aaa\",\"expires_in\":7200}");
        enqueueOkSend();

        MessageSendResult r = sender.send(msg(1));
        assertTrue(r.isSuccess(), "正例对照：errcode=0 成功");
        assertEquals("9527", r.getProviderMessageId());

        StubServer.Recorded tok = stub.last("/cgi-bin/token");
        assertEquals("GET", tok.method);
        Map<String, String> tq = tok.query();
        assertEquals("client_credential", tq.get("grant_type"));
        assertEquals(APP_ID, tq.get("appid"));
        assertEquals(APP_SECRET, tq.get("secret"), "凭据必须真的在请求里（也是日志测试的猎物前提）");

        StubServer.Recorded snd = stub.last("/cgi-bin/message/template/send");
        assertEquals("POST", snd.method);
        assertEquals("tok-aaa", snd.query().get("access_token"));
        Map<String, Object> body = MsgJson.toMap(snd.body);
        assertEquals("OPENID-user-1", body.get("touser"));
        assertEquals(TEMPLATE_ID, body.get("template_id"));
        assertEquals("https://shop.example.com/order/1", body.get("url"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertNotNull(data);
        @SuppressWarnings("unchecked")
        Map<String, Object> cell = (Map<String, Object>) data.get("character_string1");
        assertEquals("ORD1", cell.get("value"), "模板参数必须包成 {\"value\":...}");
        assertEquals(2, data.size());
    }

    @Test
    void nMessagesFetchTokenOnce() {
        stub.enqueue("/cgi-bin/token", 200, "{\"access_token\":\"tok-once\",\"expires_in\":7200}");
        for (int i = 0; i < 5; i++) {
            enqueueOkSend();
        }

        for (int i = 0; i < 5; i++) {
            assertTrue(sender.send(msg(i)).isSuccess(), "第 " + i + " 条应成功");
        }
        assertEquals(1, stub.countFor("/cgi-bin/token"), "5 条消息只允许取 1 次 token");
        assertEquals(5, stub.countFor("/cgi-bin/message/template/send"));
        // 每条 send 都带着同一个缓存 token
        assertEquals("tok-once", stub.last("/cgi-bin/message/template/send").query().get("access_token"));
    }

    @Test
    void expiredTokenErrcodeInvalidatesCacheAndRetriesOnce() {
        stub.enqueue("/cgi-bin/token", 200, "{\"access_token\":\"tok-stale\",\"expires_in\":7200}");
        stub.enqueue("/cgi-bin/message/template/send", 200,
                "{\"errcode\":42001,\"errmsg\":\"access_token expired\"}");
        stub.enqueue("/cgi-bin/token", 200, "{\"access_token\":\"tok-fresh\",\"expires_in\":7200}");
        enqueueOkSend();

        MessageSendResult r = sender.send(msg(1));

        assertTrue(r.isSuccess(), "40001/42001 后重取重发应当成功（正例对照即本断言）");
        assertEquals(2, stub.countFor("/cgi-bin/token"), "作废旧 token、重取一次");
        assertEquals(2, stub.countFor("/cgi-bin/message/template/send"), "只重发一次，不循环");
        String secondTokenUsed = stub.last("/cgi-bin/message/template/send").query().get("access_token");
        assertEquals("tok-fresh", secondTokenUsed);
        assertNotEquals("tok-stale", secondTokenUsed);
    }

    @Test
    void otherErrcodeFailsWithoutRetry() {
        stub.enqueue("/cgi-bin/token", 200, "{\"access_token\":\"tok-a\",\"expires_in\":7200}");
        stub.enqueue("/cgi-bin/message/template/send", 200,
                "{\"errcode\":43004,\"errmsg\":\"user require subscribe\"}");
        enqueueOkSend(); // 不该被消费

        MessageSendResult r = sender.send(msg(1));
        assertFalse(r.isSuccess());
        assertEquals("WEIXIN_43004", r.getErrorCode());
        assertTrue(r.getErrorMessage().contains("user require subscribe"));
        assertEquals(1, stub.countFor("/cgi-bin/message/template/send"), "非 token 错误不重试");
        assertEquals(1, stub.countFor("/cgi-bin/token"));
    }

    @Test
    void tokenFetchFailureFailsWithoutException() {
        stub.enqueue("/cgi-bin/token", 200, "{\"errcode\":40013,\"errmsg\":\"invalid appid\"}");
        MessageSendResult r = sender.send(msg(1));
        assertFalse(r.isSuccess());
        assertEquals("WEIXIN_TOKEN_FETCH_FAILED", r.getErrorCode());
        assertEquals(0, stub.countFor("/cgi-bin/message/template/send"));
    }

    @Test
    void missingCredentialsMeansNotReady() {
        props.put(Channels.IM_WEIXIN_MP, new ChannelsProperties.ChannelCfg());
        assertFalse(sender.ready());
    }
}
