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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 企业微信群机器人：key 走 query、body 形状、errcode!=0 ⇒ fail 且 errmsg 上带原文
 * （任务书点名行为），同测试内正例对照。
 */
class WecomRobotSenderTest {

    private static final String KEY = "wecom-webhook-key-9527";

    private StubServer stub;
    private ChannelsProperties props;
    private WecomRobotSender sender;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubServer();
        props = new ChannelsProperties();
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        cfg.setToken(KEY);
        props.put(Channels.IM_WECOM, cfg);
        sender = new WecomRobotSender(props, new SimpleHttpClient());
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static Message msg() {
        return Message.builder().channel(Channels.IM_WECOM).bizType("TODO")
                .subject("待办").content("你有 3 条待审批工单").build();
    }

    @Test
    void keyInQueryAndTextBody() {
        stub.enqueue("/cgi-bin/webhook/send", 200, "{\"errcode\":0,\"errmsg\":\"ok\"}");

        MessageSendResult r = sender.send(msg());
        assertTrue(r.isSuccess(), "正例对照：errcode=0 必须成功");

        StubServer.Recorded req = stub.last("/cgi-bin/webhook/send");
        assertEquals("POST", req.method);
        assertEquals(KEY, req.query().get("key"));
        Map<String, Object> body = MsgJson.toMap(req.body);
        assertEquals("text", body.get("msgtype"));
        @SuppressWarnings("unchecked")
        Map<String, Object> text = (Map<String, Object>) body.get("text");
        assertEquals("待办\n你有 3 条待审批工单", text.get("content"));
        assertFalse(text.containsKey("mentioned_mobile_list"), "不@人时不带该字段");
    }

    @Test
    void mentionedMobilesFromVendorOptions() {
        stub.enqueue("/cgi-bin/webhook/send", 200, "{\"errcode\":0,\"errmsg\":\"ok\"}");
        Message m = msg();
        m.getVendorOptions().put("mentionedMobiles", "13800000001,13800000002");

        assertTrue(sender.send(m).isSuccess(), "正例对照");

        Map<String, Object> body = MsgJson.toMap(stub.last("/cgi-bin/webhook/send").body);
        @SuppressWarnings("unchecked")
        Map<String, Object> text = (Map<String, Object>) body.get("text");
        assertEquals(java.util.Arrays.asList("13800000001", "13800000002"),
                text.get("mentioned_mobile_list"));
    }

    @Test
    void nonZeroErrcodeFailsWithErrmsgAndNoException() {
        stub.enqueue("/cgi-bin/webhook/send", 200, "{\"errcode\":0,\"errmsg\":\"ok\"}");
        stub.enqueue("/cgi-bin/webhook/send", 200,
                "{\"errcode\":93000,\"errmsg\":\"invalid webhook url\"}");

        assertTrue(sender.send(msg()).isSuccess(), "正例对照：同一路径先跑通成功");

        MessageSendResult r = sender.send(msg());
        assertFalse(r.isSuccess());
        assertEquals("WECOM_93000", r.getErrorCode());
        assertTrue(r.getErrorMessage().contains("invalid webhook url"));
        assertEquals(2, stub.countFor("/cgi-bin/webhook/send"));
    }

    @Test
    void missingKeyMeansNotReady() {
        props.put(Channels.IM_WECOM, new ChannelsProperties.ChannelCfg());
        assertFalse(sender.ready());
    }
}
