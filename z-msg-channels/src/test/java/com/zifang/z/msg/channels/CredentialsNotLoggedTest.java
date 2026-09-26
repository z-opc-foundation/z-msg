package com.zifang.z.msg.channels;

import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.channels.config.ChannelsProperties;
import com.zifang.z.msg.channels.http.SimpleHttpClient;
import com.zifang.z.msg.channels.provider.AliyunSmsSender;
import com.zifang.z.msg.channels.provider.DingTalkRobotSender;
import com.zifang.z.msg.channels.provider.JPushSender;
import com.zifang.z.msg.channels.provider.SlackBotSender;
import com.zifang.z.msg.channels.provider.TencentSmsSender;
import com.zifang.z.msg.channels.provider.WeixinMpSender;
import com.zifang.z.msg.channels.support.LogCapture;
import com.zifang.z.msg.channels.support.StubServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 凭据不进日志（ChannelSender 侧的安全不变量）。
 * <p>
 * 猎物先行：每例都先从 stub 侧断言"凭据真的进了请求原文"（query/header 里能抓到），
 * 否则"日志里没有"就是空跑。日志用捕获 appender 收，且断言"捕获非空 + 含本次发送痕迹"，
 * 证明 appender 挂对了地方（log4j2-test.xml 的 com.zifang 是 additivity=false）。
 */
class CredentialsNotLoggedTest {

    private static final String DT_TOKEN = "dtSuperSecretToken123";
    private static final String DT_SECRET = "SECdTSuperSecret456";
    private static final String SLACK_TOKEN = "xoxb-SuperSecret789";
    private static final String WX_SECRET = "wxSuperSecret000";
    private static final String AK_SECRET = "aliyunSuperSecret111";
    private static final String TX_SECRET_ID = "AKID" + "zSuperSecretId000";
    private static final String TX_SECRET_KEY = "Gu5t9xSuperSecretKey111";
    private static final String JP_APP_KEY = "jpushAppKeySuper333";
    private static final String JP_MASTER_SECRET = "jpushMasterSuper444";

    private StubServer stub;
    private ChannelsProperties props;
    private LogCapture logs;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubServer();
        props = new ChannelsProperties();
        put("im-dingtalk", "IM_DINGTALK");
        logs = LogCapture.start();
    }

    private void put(String ignored, String channel) {
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        props.put(channel, cfg);
    }

    @AfterEach
    void tearDown() {
        logs.close();
        stub.close();
    }

    private static Message msg(String channel) {
        return Message.builder().channel(channel).bizType("SECURITY_ALERT")
                .subject("alert").content("body").receiver("someone").build();
    }

    @Test
    void dingTalkTokenSecretAndSignAreNotLoggedButAreOnTheWire() {
        ChannelsProperties.ChannelCfg cfg = props.channel("IM_DINGTALK");
        cfg.setToken(DT_TOKEN);
        cfg.setSecret(DT_SECRET);
        // 让 provider 至少写一条日志：500 会走 retry/fail 路径
        stub.enqueue("/robot/send", 500, "gateway exploded");

        DingTalkRobotSender sender = new DingTalkRobotSender(props, new SimpleHttpClient());
        MessageSendResult r = sender.send(msg(Channels.IM_DINGTALK));

        assertFalse(r.isSuccess());
        // 猎物：token 与派生的 sign 都真进了请求原文
        StubServer.Recorded req = stub.last("/robot/send");
        Map<String, String> q = req.query();
        assertEquals(DT_TOKEN, q.get("access_token"), "猎物必须在 query 里");
        assertTrue(q.containsKey("sign") && !q.get("sign").isEmpty());

        String text = logs.text();
        assertFalse(text.isEmpty(), "appender 必须收到日志（否则下面的负断言是空跑）");
        assertTrue(text.contains("127.0.0.1") || text.contains("robot/send"),
                "日志里应有本次发送的 host/path 痕迹: " + text);
        assertFalse(text.contains(DT_TOKEN), "access_token 不得进日志");
        assertFalse(text.contains(DT_SECRET), "secret 不得进日志");
        assertFalse(text.contains(q.get("sign")), "sign 值不得进日志");
    }

    @Test
    void slackBearerTokenIsNotLoggedButIsOnTheWire() {
        ChannelsProperties.ChannelCfg cfg = props.channel("IM_DINGTALK");
        cfg.setBaseUrl(stub.baseUrl()); // 占位避免空 map
        ChannelsProperties.ChannelCfg slack = new ChannelsProperties.ChannelCfg();
        slack.setBaseUrl(stub.baseUrl());
        slack.setToken(SLACK_TOKEN);
        slack.setSlackChannel("#chan");
        props.put("IM_SLACK", slack);
        stub.enqueue("/api/chat.postMessage", 500, "boom");

        SlackBotSender sender = new SlackBotSender(props, new SimpleHttpClient());
        assertFalse(sender.send(msg(Channels.IM_SLACK)).isSuccess());

        StubServer.Recorded req = stub.last("/api/chat.postMessage");
        assertEquals("Bearer " + SLACK_TOKEN, req.header("authorization"), "猎物：token 真在 header 里");

        String text = logs.text();
        assertFalse(text.isEmpty());
        assertFalse(text.contains(SLACK_TOKEN), "Bearer token 不得进日志");
    }

    @Test
    void weixinAppSecretAndAccessTokenNotLoggedButOnWire() {
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        cfg.setAppId("wxid123");
        cfg.setAppSecret(WX_SECRET);
        cfg.setTemplateCode("TPL1");
        props.put("IM_WEIXIN_MP", cfg);
        stub.enqueue("/cgi-bin/token", 200, "{\"access_token\":\"liveAccessToken999\",\"expires_in\":7200}");
        stub.enqueue("/cgi-bin/message/template/send", 500, "server error");

        WeixinMpSender sender = new WeixinMpSender(props, new SimpleHttpClient());
        MessageSendResult r = sender.send(msg(Channels.IM_WEIXIN_MP));
        assertFalse(r.isSuccess());

        StubServer.Recorded tok = stub.last("/cgi-bin/token");
        assertEquals(WX_SECRET, tok.query().get("secret"), "猎物：appSecret 在 token 请求 query 里");
        StubServer.Recorded snd = stub.last("/cgi-bin/message/template/send");
        assertEquals("liveAccessToken999", snd.query().get("access_token"), "猎物：access_token 在 send query 里");

        String text = logs.text();
        assertFalse(text.isEmpty());
        assertFalse(text.contains(WX_SECRET), "appSecret 不得进日志");
        assertFalse(text.contains("liveAccessToken999"), "access_token 不得进日志");
    }

    @Test
    void aliyunAccessKeySecretAndSignatureNotLoggedButOnWire() {
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        cfg.setAccessKeyId("AKIDsuper");
        cfg.setAccessKeySecret(AK_SECRET);
        cfg.setSignName("z-opc");
        cfg.setTemplateCode("SMS_1");
        props.put("SMS", cfg);
        stub.enqueue("/", 500, "boom");

        AliyunSmsSender sender = new AliyunSmsSender(props, new SimpleHttpClient(),
                () -> "nonce-log-test", () -> "2023-07-22T08:00:00Z");
        Message m = msg(Channels.SMS);
        m.setReceiver("13800000000");
        m.getParams().put("code", "1234");
        assertFalse(sender.send(m).isSuccess());

        StubServer.Recorded req = stub.last("/");
        String raw = req.rawQuery;
        assertTrue(raw.contains("Signature="), "猎物：签名随 query 上了线");

        String text = logs.text();
        assertFalse(text.isEmpty());
        assertFalse(text.contains(AK_SECRET), "AccessKeySecret 不得进日志");
        // 派生的 Signature 值也不许出现（HMAC-SHA1 的 Base64 里可能含 =，取中段比对）
        String sig = req.query().get("Signature");
        assertTrue(sig.length() > 10);
        assertFalse(text.contains(sig.substring(5, sig.length() - 5)), "签名派生值不得进日志");
    }

    @Test
    void tencentSecretPairAndDerivedSignatureAreNotLoggedButTheHeaderIsOnTheWire() {
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        cfg.setAccessKeyId(TX_SECRET_ID);
        cfg.setAccessKeySecret(TX_SECRET_KEY);
        cfg.setSdkAppId("1400009100");
        cfg.setSignName("z-opc");
        cfg.setTemplateCode("1648");
        props.put(Channels.SMS, cfg);
        stub.enqueue("/", 500, "boom");

        TencentSmsSender sender = new TencentSmsSender(props, new SimpleHttpClient(), () -> "1551113065");
        Message m = msg(Channels.SMS);
        m.setReceiver("13800000000");
        m.getParams().put("code", "1234");
        assertFalse(sender.send(m).isSuccess());

        // 猎物：Authorization 真上了线，SecretKey 只以派生签名的形式出现
        String auth = stub.last("/").header("authorization");
        assertTrue(auth != null && auth.startsWith("TC3-HMAC-SHA256 Credential="), "实际=" + auth);
        assertTrue(auth.contains(TX_SECRET_ID + "/"), "SecretId 必须在 Authorization 里");
        String sig = auth.substring(auth.lastIndexOf("Signature=") + "Signature=".length());
        assertEquals(64, sig.length(), "TC3 签名是 64 位小写十六进制，实际=" + sig);

        String text = logs.text();
        assertFalse(text.isEmpty());
        assertFalse(text.contains(TX_SECRET_KEY), "SecretKey 不得进日志");
        assertFalse(text.contains(TX_SECRET_ID), "SecretId 是凭据的另一半，同样不得进日志");
        assertFalse(text.contains(sig), "派生签名不得进日志");
    }

    @Test
    void jpushBasicCredentialIsNotLoggedButIsOnTheWire() {
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        cfg.setAppKey(JP_APP_KEY);
        cfg.setMasterSecret(JP_MASTER_SECRET);
        props.put(Channels.PUSH_JPUSH, cfg);
        stub.enqueue("/v3/push", 500, "boom");

        JPushSender sender = new JPushSender(props, new SimpleHttpClient());
        assertFalse(sender.send(msg(Channels.PUSH_JPUSH)).isSuccess());

        String encoded = java.util.Base64.getEncoder().encodeToString(
                (JP_APP_KEY + ":" + JP_MASTER_SECRET).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("Basic " + encoded, stub.last("/v3/push").header("authorization"),
                "猎物：AppKey+MasterSecret 整段真进了 header");

        String text = logs.text();
        assertFalse(text.isEmpty());
        assertFalse(text.contains(JP_MASTER_SECRET), "Master Secret 不得进日志");
        assertFalse(text.contains(encoded), "Base64 只是编码不是加密，密文同样不得进日志");
        assertFalse(text.contains(JP_APP_KEY), "App Key 不得进日志");
    }
}
