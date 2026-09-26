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
 * Slack chat.postMessage：Bearer header、body channel/text、ok:false ⇒ fail。
 */
class SlackBotSenderTest {

    private static final String BOT_TOKEN = "xoxb-slack-bot-token-SECRET";

    private StubServer stub;
    private ChannelsProperties props;
    private SlackBotSender sender;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubServer();
        props = new ChannelsProperties();
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        cfg.setToken(BOT_TOKEN);
        cfg.setSlackChannel("#ops-alerts");
        props.put(Channels.IM_SLACK, cfg);
        sender = new SlackBotSender(props, new SimpleHttpClient());
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static Message msg() {
        return Message.builder().channel(Channels.IM_SLACK).bizType("ALERT")
                .subject("disk").content("disk > 90% on node-3").build();
    }

    @Test
    void bearerHeaderAndChannelFromReceiver() {
        stub.enqueue("/api/chat.postMessage", 200,
                "{\"ok\":true,\"channel\":\"C0123\",\"ts\":\"1690000000.000100\"}");

        Message m = msg();
        m.setReceiver("C0123");
        MessageSendResult r = sender.send(m);

        assertTrue(r.isSuccess(), "正例对照：ok:true 必须成功");
        assertEquals("1690000000.000100", r.getProviderMessageId(), "ts 应作为 providerMessageId");

        StubServer.Recorded req = stub.last("/api/chat.postMessage");
        assertEquals("POST", req.method);
        assertEquals("Bearer " + BOT_TOKEN, req.header("authorization"));
        assertTrue(req.header("content-type").startsWith("application/json"));
        Map<String, Object> body = MsgJson.toMap(req.body);
        assertEquals("C0123", body.get("channel"));
        assertEquals("disk\ndisk > 90% on node-3", body.get("text"));
    }

    @Test
    void receiverFallsBackToConfiguredSlackChannel() {
        stub.enqueue("/api/chat.postMessage", 200, "{\"ok\":true,\"ts\":\"1.1\"}");
        MessageSendResult r = sender.send(msg());
        assertTrue(r.isSuccess(), "正例对照");
        Map<String, Object> body = MsgJson.toMap(stub.last("/api/chat.postMessage").body);
        assertEquals("#ops-alerts", body.get("channel"));
    }

    @Test
    void okFalseIsBusinessRejectionNotException() {
        stub.enqueue("/api/chat.postMessage", 200, "{\"ok\":true,\"ts\":\"1.1\"}");
        stub.enqueue("/api/chat.postMessage", 200, "{\"ok\":false,\"error\":\"not_authed\"}");

        assertTrue(sender.send(msg()).isSuccess(), "正例对照：ok:true 成功");

        MessageSendResult r = sender.send(msg());
        assertFalse(r.isSuccess());
        assertEquals("SLACK_not_authed", r.getErrorCode());
        assertTrue(r.getErrorMessage().contains("not_authed"));
        assertEquals(2, stub.countFor("/api/chat.postMessage"));
    }

    @Test
    void emptyReceiverAndNoDefaultChannelFailsBeforeHttp() {
        props.channel(Channels.IM_SLACK).setSlackChannel(null);
        Message m = msg();
        m.setReceiver(null);
        MessageSendResult r = sender.send(m);
        assertFalse(r.isSuccess());
        assertEquals("INVALID_RECEIVER", r.getErrorCode());
        assertEquals(0, stub.countFor("/api/chat.postMessage"), "参数不全不该发 HTTP");
    }

    @Test
    void missingTokenMeansNotReady() {
        props.put(Channels.IM_SLACK, new ChannelsProperties.ChannelCfg());
        assertFalse(sender.ready());
    }
}
