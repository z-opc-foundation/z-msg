package com.zifang.z.msg.channels.provider;

import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.channels.config.ChannelsProperties;
import com.zifang.z.msg.channels.http.SimpleHttpClient;
import com.zifang.z.msg.channels.support.StubServer;
import com.zifang.z.msg.channels.support.TestSigns;
import com.zifang.z.msg.core.json.MsgJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 飞书群机器人：hook 路径、body 形状（msg_type/content.text）、可选加签字段进 body。
 * 签名独立重算对照（注意：timestamp 是秒，密钥是 "ts\nsecret"、数据是空串）。
 */
class FeishuRobotSenderTest {

    private static final String HOOK_TOKEN = "feishu-hook-token-ABC";
    private static final String SECRET = "feishu_test_secret";

    private StubServer stub;
    private ChannelsProperties props;
    private FeishuRobotSender sender;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubServer();
        props = new ChannelsProperties();
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        cfg.setToken(HOOK_TOKEN);
        cfg.setSecret(SECRET);
        props.put(Channels.IM_FEISHU, cfg);
        sender = new FeishuRobotSender(props, new SimpleHttpClient());
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static Message msg() {
        return Message.builder().channel(Channels.IM_FEISHU).bizType("SECURITY_ALERT")
                .subject("安全告警").content("检测到异地登录").build();
    }

    @Test
    void hookPathAndBodyShapeAndSign() {
        stub.enqueue("/open-apis/bot/v2/hook/" + HOOK_TOKEN, 200, "{\"code\":0,\"msg\":\"success\"}");

        MessageSendResult r = sender.send(msg());
        assertTrue(r.isSuccess(), "code=0 应当成功（正例对照）: " + r.getErrorMessage());

        StubServer.Recorded req = stub.last("/open-apis/bot/v2/hook/" + HOOK_TOKEN);
        assertEquals("POST", req.method);
        Map<String, Object> body = MsgJson.toMap(req.body);
        assertEquals("text", body.get("msg_type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) body.get("content");
        assertEquals("安全告警\n检测到异地登录", content.get("text"));

        String ts = String.valueOf(body.get("timestamp"));
        assertNotNull(ts);
        assertTrue(ts.length() == 10, "飞书 timestamp 是秒级（10 位）, 实际: " + ts);
        assertEquals(TestSigns.feishuSign(SECRET, ts), body.get("sign"),
                "sign 应等于独立实现 (key=ts\\nsecret, data=空) 对捕获 timestamp 的重算值");
    }

    @Test
    void noSecretMeansNoTimestampAndSignInBody() {
        props.channel(Channels.IM_FEISHU).setSecret(null);
        stub.enqueue("/open-apis/bot/v2/hook/" + HOOK_TOKEN, 200, "{\"code\":0,\"msg\":\"success\"}");
        assertTrue(sender.send(msg()).isSuccess(), "正例对照");
        Map<String, Object> body = MsgJson.toMap(stub.last("/open-apis/bot/v2/hook/" + HOOK_TOKEN).body);
        assertFalse(body.containsKey("sign"));
        assertFalse(body.containsKey("timestamp"));
    }

    @Test
    void nonZeroCodeIsBusinessRejectionNotException() {
        stub.enqueue("/open-apis/bot/v2/hook/" + HOOK_TOKEN, 200, "{\"code\":0,\"msg\":\"success\"}");
        stub.enqueue("/open-apis/bot/v2/hook/" + HOOK_TOKEN, 200,
                "{\"code\":19021,\"msg\":\"sign match fail\"}");

        assertTrue(sender.send(msg()).isSuccess(), "正例对照：code=0 成功");

        MessageSendResult r = sender.send(msg());
        assertFalse(r.isSuccess());
        assertEquals("FEISHU_19021", r.getErrorCode());
        assertTrue(r.getErrorMessage().contains("sign match fail"));
    }

    @Test
    void missingTokenAndUrlMeansNotReady() {
        props.put(Channels.IM_FEISHU, new ChannelsProperties.ChannelCfg());
        assertFalse(sender.ready());
    }
}
