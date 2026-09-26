package com.zifang.z.msg.channels;

import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.channels.config.ChannelsProperties;
import com.zifang.z.msg.channels.http.HttpResponse;
import com.zifang.z.msg.channels.http.SimpleHttpClient;
import com.zifang.z.msg.channels.provider.DingTalkRobotSender;
import com.zifang.z.msg.channels.support.StubServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 共享 HTTP 层的四条规矩：超时快速失败（有耗时上限）、重试只吃传输错误/5xx、
 * 响应体有限读取、非 http(s) 协议直接拒。全部对着本地 stub 跑，不联网。
 */
class HttpResilienceTest {

    private StubServer stub;
    private ChannelsProperties props;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubServer();
        props = new ChannelsProperties();
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        cfg.setToken("tok");
        props.put(Channels.IM_DINGTALK, cfg);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private DingTalkRobotSender senderWith(Integer readMs, Integer connectMs, int retries) {
        ChannelsProperties.ChannelCfg cfg = props.channel(Channels.IM_DINGTALK);
        cfg.setReadTimeoutMs(readMs);
        cfg.setConnectTimeoutMs(connectMs);
        cfg.setRetries(retries);
        return new DingTalkRobotSender(props, new SimpleHttpClient());
    }

    private static Message msg() {
        return Message.builder().channel(Channels.IM_DINGTALK).bizType("X").content("hi").build();
    }

    @Test
    void slowServerTriggersFastTimeoutNotHang() {
        // stub 侧有界 sleep 800ms（模拟慢服务端），客户端读超时 150ms
        stub.enqueue("/robot/send", 200, "{\"errcode\":0}", 800);
        DingTalkRobotSender sender = senderWith(150, 150, 0);

        long start = System.currentTimeMillis();
        MessageSendResult r = sender.send(msg());
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(r.isSuccess());
        assertTrue("HTTP_TIMEOUT".equals(r.getErrorCode()) || "HTTP_TRANSPORT_ERROR".equals(r.getErrorCode()),
                "应为超时/传输错误码, 实际: " + r.getErrorCode());
        assertTrue(elapsed < 2000, "读超时 150ms 却耗了 " + elapsed + "ms —— 超时没生效或在挂");
    }

    @Test
    void configuredTimeoutsAreClampedToHardCeilings() {
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setConnectTimeoutMs(9999999);
        cfg.setReadTimeoutMs(9999999);
        assertEquals(ChannelsProperties.MAX_CONNECT_TIMEOUT_MS, cfg.connectTimeoutMsOrDefault());
        assertEquals(ChannelsProperties.MAX_READ_TIMEOUT_MS, cfg.readTimeoutMsOrDefault());
        // 0 / 负数不允许表达"无限等"
        cfg.setReadTimeoutMs(0);
        cfg.setConnectTimeoutMs(-5);
        assertEquals(ChannelsProperties.DEFAULT_CONNECT_TIMEOUT_MS, cfg.connectTimeoutMsOrDefault());
        assertEquals(ChannelsProperties.DEFAULT_READ_TIMEOUT_MS, cfg.readTimeoutMsOrDefault());
    }

    @Test
    void connectionRefusedFailsWithoutException() {
        // 端口 1 基本不会有人监听：立刻拒连
        props.channel(Channels.IM_DINGTALK).setBaseUrl("http://127.0.0.1:1");
        MessageSendResult r = senderWith(200, 200, 0).send(msg());
        assertFalse(r.isSuccess());
        assertEquals("HTTP_TRANSPORT_ERROR", r.getErrorCode());
    }

    @Test
    void http5xxIsRetriedAndCanRecover() {
        stub.enqueue("/robot/send", 502, "bad gateway");
        stub.enqueue("/robot/send", 200, "{\"errcode\":0,\"errmsg\":\"ok\"}");
        DingTalkRobotSender sender = senderWith(1000, 500, 1);

        MessageSendResult r = sender.send(msg());

        assertTrue(r.isSuccess(), "502 后重试拿到 errcode=0 应成功");
        assertEquals(2, stub.countFor("/robot/send"));
    }

    @Test
    void businessRejectionIsNotRetriedAtHttpLayer() {
        stub.enqueue("/robot/send", 200, "{\"errcode\":310000,\"errmsg\":\"no keyword\"}");
        DingTalkRobotSender sender = senderWith(1000, 500, 3);

        MessageSendResult r = sender.send(msg());

        assertFalse(r.isSuccess());
        assertEquals(1, stub.countFor("/robot/send"), "业务拒绝（2xx+errcode）不得在 HTTP 层重试");
    }

    @Test
    void responseBodyReadIsLimited() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 16384; i++) {
            big.append("0123456789abcdef"); // 16B * 16384 = 256KB > 64KB 上限
        }
        stub.defaultResponse(200, big.toString());
        SimpleHttpClient client = new SimpleHttpClient();

        HttpResponse resp = client.get(stub.baseUrl() + "/anything", null, 1000, 3000);

        assertTrue(resp.is2xx());
        assertEquals(ChannelsProperties.MAX_RESPONSE_BYTES, resp.getBody().length(),
                "响应体必须截断在硬上限");
    }

    @Test
    void nonHttpSchemeIsRejected() {
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse resp = client.get("file:///etc/passwd", null, 500, 500);
        assertFalse(resp.is2xx());
        assertEquals("HTTP_UNSUPPORTED_SCHEME", resp.getErrorCode());
    }
}
