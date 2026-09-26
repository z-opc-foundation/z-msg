package com.zifang.z.msg.channels.provider;

import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.channels.config.ChannelsProperties;
import com.zifang.z.msg.channels.http.SimpleHttpClient;
import com.zifang.z.msg.channels.support.StubServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 极光推送 v3（Basic 认证 + JSON body）。
 * <p>
 * Authorization 的期望值是离线（python3 base64）算好的定值，不在此调 {@code Signatures.basicAuth}
 * 现算现比；"发给谁"的四种形状（registration_id / all / alias / tag）逐个钉，
 * 因为这四支是极光 API 与站内信语义差别最大的地方。
 */
class JPushSenderTest {

    private static final String APP_KEY = "apikeyJPUSH2200";
    private static final String MASTER_SECRET = "公司";
    /** python3: base64.b64encode("apikeyJPUSH2200:公司".encode('utf-8')) */
    private static final String BASIC = "Basic YXBpa2V5SlBVU0gyMjAwOuWFrOWPuA==";

    private StubServer stub;
    private ChannelsProperties props;
    private JPushSender sender;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubServer();
        props = new ChannelsProperties();
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        cfg.setAppKey(APP_KEY);
        cfg.setMasterSecret(MASTER_SECRET);
        props.put(Channels.PUSH_JPUSH, cfg);
        sender = new JPushSender(props, new SimpleHttpClient());
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static Message push(String receiver) {
        return Message.builder().channel(Channels.PUSH_JPUSH).receiver(receiver)
                .bizType("ORDER").subject("标题").content("正文").build();
    }

    @Test
    void basicAuthHeaderIsExactlyTheOfflineVector() {
        stub.enqueue("/v3/push", 200, "{\"msg_id\":\"3596752105\"}");

        MessageSendResult r = sender.send(push("reg-device-1"));
        assertTrue(r.isSuccess(), "正例对照：200 + msg_id 算成功");
        assertEquals("3596752105", r.getProviderMessageId());

        StubServer.Recorded req = stub.last("/v3/push");
        assertEquals("POST", req.method);
        assertEquals(BASIC, req.header("authorization"),
                "猎物：AppKey:MasterSecret 的 Base64 整段真进了 header（否则下面的日志负断言是空跑）");
        assertEquals("application/json; charset=UTF-8", req.header("content-type"));
    }

    @Test
    void bodyCarriesPlatformAudienceAndBothOsNotifications() {
        stub.defaultResponse(200, "{\"msg_id\":\"1\"}");
        assertTrue(sender.send(push("reg-device-1")).isSuccess());

        assertEquals("{\"platform\":\"all\","
                        + "\"audience\":{\"registration_id\":[\"reg-device-1\"]},"
                        + "\"notification\":{\"android\":{\"alert\":\"正文\",\"title\":\"标题\"},"
                        + "\"ios\":{\"alert\":\"正文\",\"title\":\"标题\"}}}",
                stub.last("/v3/push").body);
    }

    @Test
    void receiverAllIsBroadcastButAliasAndTagAddressGroups() {
        stub.defaultResponse(200, "{\"msg_id\":\"2\"}");

        assertTrue(sender.send(push("all")).isSuccess());
        assertTrue(stub.last("/v3/push").body.contains("\"audience\":\"all\""),
                "receiver=all 要翻成极光的广播形状，不是 registration_id:[\"all\"]");

        Message alias = push("ignored");
        alias.getParams().put("alias", "user-1, user-2");
        assertTrue(sender.send(alias).isSuccess());
        assertTrue(stub.last("/v3/push").body.contains("\"audience\":{\"alias\":[\"user-1\",\"user-2\"]}"),
                "alias 优先于 receiver: " + stub.last("/v3/push").body);

        Message tag = push("reg-device-1");
        tag.getParams().put("tag", "vip");
        assertTrue(sender.send(tag).isSuccess());
        assertTrue(stub.last("/v3/push").body.contains("\"audience\":{\"tag\":[\"vip\"]}"));
    }

    @Test
    void platformParamNarrowsTargets() {
        stub.defaultResponse(200, "{\"msg_id\":\"3\"}");
        Message m = push("reg-device-1");
        m.getParams().put("platform", "android");
        assertTrue(sender.send(m).isSuccess());
        assertTrue(stub.last("/v3/push").body.startsWith("{\"platform\":[\"android\"],"),
                "单值 platform 翻成数组: " + stub.last("/v3/push").body);

        m.getParams().put("platform", "android,ios");
        assertTrue(sender.send(m).isSuccess());
        assertTrue(stub.last("/v3/push").body.contains("\"platform\":[\"android\",\"ios\"]"));
    }

    @Test
    void contentFallsBackToSubjectAndEmptyBothFailsLocally() {
        stub.defaultResponse(200, "{\"msg_id\":\"4\"}");
        Message onlyTitle = Message.builder().channel(Channels.PUSH_JPUSH).receiver("r-1")
                .bizType("ORDER").subject("只有标题").build();
        assertTrue(sender.send(onlyTitle).isSuccess());
        assertTrue(stub.last("/v3/push").body.contains("\"alert\":\"只有标题\""),
                "content 缺位时 subject 顶上: " + stub.last("/v3/push").body);

        Message empty = Message.builder().channel(Channels.PUSH_JPUSH).receiver("r-1")
                .bizType("ORDER").build();
        MessageSendResult r = sender.send(empty);
        assertFalse(r.isSuccess());
        assertEquals("INVALID_CONTENT", r.getErrorCode());
        assertEquals(1, stub.countFor("/v3/push"),
                "只有前一条只有标题的请求发出去了；正文全空这条必须短路");
    }

    @Test
    void blankReceiverFailsWithInvalidReceiver() {
        MessageSendResult r = sender.send(push("  "));
        assertFalse(r.isSuccess());
        assertEquals("INVALID_RECEIVER", r.getErrorCode());
        assertEquals(0, stub.countFor("/v3/push"), "不知道发给谁就不该发那次请求");
    }

    @Test
    void errorBodyMapsToJpushCodeWithoutRetry() {
        props.channel(Channels.PUSH_JPUSH).setRetries(2);
        stub.enqueue("/v3/push", 400, "{\"error\":{\"code\":1004,\"msg\":\"消息内容含敏感词\"}}");

        MessageSendResult r = sender.send(push("reg-device-1"));
        assertFalse(r.isSuccess());
        assertEquals("JPUSH_1004", r.getErrorCode());
        assertTrue(r.getErrorMessage().contains("敏感词"));
        assertEquals(1, stub.countFor("/v3/push"), "4xx 是确定性拒绝，HTTP 层不许重试");
    }

    @Test
    void transportFailureAndBadJsonBecomeStructuredFails() {
        stub.enqueue("/v3/push", 200, "not json");
        MessageSendResult bad = sender.send(push("reg-device-1"));
        assertFalse(bad.isSuccess());
        assertEquals("JPUSH_BAD_RESPONSE", bad.getErrorCode());

        stub.close();
        MessageSendResult down = sender.send(push("reg-device-1"));
        assertFalse(down.isSuccess(), "端口关掉后必须 fail，不能抛异常");
        assertTrue(down.getErrorCode().startsWith("HTTP_"), "实际=" + down.getErrorCode());
    }

    @Test
    void missingKeysMeansNotReady() {
        props.put(Channels.PUSH_JPUSH, new ChannelsProperties.ChannelCfg());
        assertFalse(sender.ready());
        ChannelsProperties.ChannelCfg half = new ChannelsProperties.ChannelCfg();
        half.setAppKey(APP_KEY);
        props.put(Channels.PUSH_JPUSH, half);
        assertFalse(sender.ready(), "有 AppKey 没 Master Secret 也发不出去");
    }

    @Test
    void providerNameAndChannelAreTheContract() {
        assertEquals("jpush", sender.provider());
        assertEquals(Channels.PUSH_JPUSH, sender.channel());
    }
}
