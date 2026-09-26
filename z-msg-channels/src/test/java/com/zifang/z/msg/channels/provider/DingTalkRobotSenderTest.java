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
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉钉群机器人：钉请求形状（method/path/query/body）与响应翻译。
 * 签名在测试里用独立实现（{@code TestSigns}）对被捕获的 timestamp 重算对照。
 */
class DingTalkRobotSenderTest {

    private static final String TOKEN = "dt-access-token-XYZ";
    private static final String SECRET = "SECding_test_secret";

    private StubServer stub;
    private ChannelsProperties props;
    private DingTalkRobotSender sender;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubServer();
        props = new ChannelsProperties();
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        cfg.setToken(TOKEN);
        cfg.setSecret(SECRET);
        props.put(Channels.IM_DINGTALK, cfg);
        sender = new DingTalkRobotSender(props, new SimpleHttpClient());
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static Message msg() {
        return Message.builder()
                .channel(Channels.IM_DINGTALK)
                .receiver("robot")
                .bizType("ORDER_PAID")
                .subject("订单已支付")
                .content("订单 12345 已支付，金额 99.00 元")
                .build();
    }

    @Test
    void requestShapeMatchesOfficialSpec() {
        stub.enqueue("/robot/send", 200, "{\"errcode\":0,\"errmsg\":\"ok\"}");

        MessageSendResult r = sender.send(msg());

        // 正向对照：先钉成功路径真的走通（否则下面的"不抛异常/失败形状"都是空跑）
        assertTrue(r.isSuccess(), "errcode=0 应当成功: " + r.getErrorMessage());
        assertEquals(DingTalkRobotSender.PROVIDER, r.getProvider());

        StubServer.Recorded req = stub.last("/robot/send");
        assertEquals("POST", req.method);
        Map<String, String> q = req.query();
        assertEquals(TOKEN, q.get("access_token"));
        String ts = q.get("timestamp");
        assertNotNull(ts, "加签配置下必须带 timestamp");
        assertTrue(Long.parseLong(ts) > 1_000_000_000_000L, "钉钉用毫秒时间戳");
        // 独立重算：签名必须等于 TestSigns.dingTalkSignRaw(secret, 被捕获的 timestamp)
        // （query() 已 URL 解码，所以对照未编码的 Base64 原文；编码规则由 SignaturesTest 钉）
        assertEquals(com.zifang.z.msg.channels.support.TestSigns.dingTalkSignRaw(SECRET, ts),
                q.get("sign"), "sign 应等于对捕获 timestamp 的独立重算值");

        Map<String, Object> body = MsgJson.toMap(req.body);
        assertEquals("text", body.get("msgtype"));
        @SuppressWarnings("unchecked")
        Map<String, Object> text = (Map<String, Object>) body.get("text");
        assertEquals("订单已支付\n订单 12345 已支付，金额 99.00 元", text.get("content"));
    }

    @Test
    void vendorAtMobilesGoIntoAtNode() {
        stub.enqueue("/robot/send", 200, "{\"errcode\":0,\"errmsg\":\"ok\"}");
        Message m = msg();
        m.setVendorOptions(new java.util.LinkedHashMap<String, Object>());
        m.getVendorOptions().put("atMobiles", Arrays.asList("13800000001", "13800000002"));

        assertTrue(sender.send(m).isSuccess());

        Map<String, Object> body = MsgJson.toMap(stub.last("/robot/send").body);
        @SuppressWarnings("unchecked")
        Map<String, Object> at = (Map<String, Object>) body.get("at");
        assertNotNull(at);
        assertEquals(Arrays.asList("13800000001", "13800000002"), at.get("atMobiles"));
        assertEquals(Boolean.FALSE, at.get("isAtAll"));
    }

    @Test
    void businessRejectionBecomesFailNotException() {
        // 负例 + 正例对照放同一条：先成功后拒绝，证明 errcode 判断不是空跑
        stub.enqueue("/robot/send", 200, "{\"errcode\":0,\"errmsg\":\"ok\"}");
        stub.enqueue("/robot/send", 200,
                "{\"errcode\":310000,\"errmsg\":\"keyword not in content\"}");

        assertTrue(sender.send(msg()).isSuccess(), "对照组：errcode=0 必须成功");

        MessageSendResult rejected = sender.send(msg());
        assertFalse(rejected.isSuccess());
        assertEquals("DINGTALK_310000", rejected.getErrorCode());
        assertTrue(rejected.getErrorMessage().contains("keyword not in content"),
                "errmsg 必须原样带上: " + rejected.getErrorMessage());
        assertEquals(2, stub.countFor("/robot/send"));
    }

    @Test
    void http500IsTransportFailure() {
        stub.enqueue("/robot/send", 500, "boom");
        MessageSendResult r = sender.send(msg());
        assertFalse(r.isSuccess());
        assertEquals("HTTP_500", r.getErrorCode());
    }

    @Test
    void noSignSecretMeansNoTimestampAndSignParams() {
        props.channel(Channels.IM_DINGTALK).setSecret(null);
        stub.enqueue("/robot/send", 200, "{\"errcode\":0,\"errmsg\":\"ok\"}");
        assertTrue(sender.send(msg()).isSuccess());
        Map<String, String> q = stub.last("/robot/send").query();
        assertEquals(TOKEN, q.get("access_token"));
        assertFalse(q.containsKey("sign"), "未配 secret 不应加签");
        assertFalse(q.containsKey("timestamp"));
    }

    @Test
    void missingTokenMeansNotReady() {
        props.put(Channels.IM_DINGTALK, new ChannelsProperties.ChannelCfg());
        assertFalse(sender.ready());
    }
}
