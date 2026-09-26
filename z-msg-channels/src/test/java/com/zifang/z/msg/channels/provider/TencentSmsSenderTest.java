package com.zifang.z.msg.channels.provider;

import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.channels.config.ChannelsProperties;
import com.zifang.z.msg.channels.http.SimpleHttpClient;
import com.zifang.z.msg.channels.support.StubServer;
import com.zifang.z.msg.channels.support.TestSigns;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 腾讯云短信（SendSms / TC3-HMAC-SHA256）。
 * <p>
 * 与阿里云那一层的分工不同：这里不重复钉算法（固定向量在 {@code SignaturesTest}），
 * 只钉"provider 把 TC3 用对了地方"——签名覆盖的就是线上真正发出的那几个字节：
 * 用 stub 捕获的 content-type / host / body 原文重算 Authorization 并逐字对照。
 * 于是"canonical 里写的 host 与 JDK 实际发的 Host 头不一致"这类只在真机上才会被拒的偏差，
 * 在离线测试里就红。
 */
class TencentSmsSenderTest {

    private static final String SID = "AKID" + "testidexample";
    private static final String SKEY = "Gu5t9secretKeyVector";
    private static final String SDK_APP_ID = "1400009100";
    private static final String TS = "1551113065";

    private StubServer stub;
    private ChannelsProperties props;
    private TencentSmsSender sender;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubServer();
        props = new ChannelsProperties();
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        cfg.setAccessKeyId(SID);
        cfg.setAccessKeySecret(SKEY);
        cfg.setSdkAppId(SDK_APP_ID);
        cfg.setSignName("z-opc测试");
        cfg.setTemplateCode("1648");
        props.put(Channels.SMS, cfg);
        sender = new TencentSmsSender(props, new SimpleHttpClient(), () -> TS);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static Message sms(String receiver) {
        return Message.builder().channel(Channels.SMS).receiver(receiver)
                .bizType("REGISTER").param("code", "8888").build();
    }

    private static String okBody() {
        return "{\"Response\":{\"SendStatusSet\":[{\"Code\":\"Ok\",\"Message\":\"send success\","
                + "\"SerialNumber\":\"serial-777\",\"PhoneNumber\":\"+8613800138000\"}],"
                + "\"RequestId\":\"req-abc\"}}";
    }

    @Test
    void authorizationIsTheSignatureOverTheBytesActuallySent() {
        stub.enqueue("/", 200, okBody());

        MessageSendResult r = sender.send(sms("13800138000"));
        assertTrue(r.isSuccess(), "正例对照：SendStatusSet[0].Code=Ok 算成功");
        assertEquals("serial-777", r.getProviderMessageId(), "供应商消息号取 SerialNumber");

        StubServer.Recorded req = stub.last("/");
        assertEquals("POST", req.method);
        assertEquals("/", req.path);
        assertEquals("", req.rawQuery == null ? "" : req.rawQuery, "参数全在 body，query 必须为空");
        assertEquals("SendSms", req.header("x-tc-action"));
        assertEquals("2021-01-11", req.header("x-tc-version"));
        assertEquals("ap-guangzhou", req.header("x-tc-region"), "没配 region 时用腾讯云短信默认地域");
        assertEquals(TS, req.header("x-tc-timestamp"));
        assertEquals("application/json; charset=utf-8", req.header("content-type"),
                "签进去的 content-type 必须与线上发出的一致");

        String host = req.header("host");
        assertTrue(host != null && host.startsWith("127.0.0.1:"),
                "猎物：stub 侧收到的 Host 是 127.0.0.1:端口，签名要吃得下带端口的 host，实际=" + host);
        String expected = TestSigns.tc3Authorization(SID, SKEY, "sms", req.header("content-type"),
                host, req.body, Long.parseLong(TS));
        // 把 SecretKey 换一个字符必须得出不同的 Authorization —— 否则上面那条等式可能是
        // "签名压根没吃 secret"，两边都退化成常量比常量。
        assertFalse(expected.equals(TestSigns.tc3Authorization(SID, SKEY + "x", "sms",
                req.header("content-type"), host, req.body, Long.parseLong(TS))),
                "独立实现必须真的吃 secret，否则这条对照没有牙");
        assertEquals(expected, req.header("authorization"),
                "Authorization 应等于用捕获原文重算的 TC3 头");
    }

    @Test
    void bodyHasTheTencentSendSmsShape() {
        stub.enqueue("/", 200, okBody());
        assertTrue(sender.send(sms("13800138000")).isSuccess(), "正例对照");

        assertEquals("{\"PhoneNumberSet\":[\"+8613800138000\"],\"SmsSdkAppId\":\"1400009100\","
                        + "\"SignName\":\"z-opc测试\",\"TemplateId\":\"1648\","
                        + "\"TemplateParamSet\":[\"8888\"]}",
                stub.last("/").body);
    }

    @Test
    void bareNumberGetsCnCountryCodeButForeignNumberPassesThrough() {
        stub.defaultResponse(200, okBody());
        assertTrue(sender.send(sms("13800138000")).isSuccess());
        assertTrue(stub.last("/").body.contains("\"PhoneNumberSet\":[\"+8613800138000\"]"));

        assertTrue(sender.send(sms("+16505551234")).isSuccess());
        assertTrue(stub.last("/").body.contains("\"PhoneNumberSet\":[\"+16505551234\"]"),
                "已带 + 的号原样送，不许再补 86");
    }

    @Test
    void malformedReceiverFailsLocallyWithoutHttpRequest() {
        MessageSendResult r = sender.send(sms("138abc"));
        assertFalse(r.isSuccess());
        assertEquals("INVALID_RECEIVER", r.getErrorCode());
        assertEquals(0, stub.countFor("/"), "补不出 E.164 就不该烧一次必然被拒的请求");
    }

    @Test
    void templateParamSetIsKeySortedAndExplicitOrderWins() {
        stub.defaultResponse(200, okBody());
        Message m = Message.builder().channel(Channels.SMS).receiver("13800138000")
                .bizType("REGISTER").build();
        m.getParams().put("b_var", "2");
        m.getParams().put("code", "8888");
        m.getParams().put("a_var", "1");
        assertTrue(sender.send(m).isSuccess());
        assertTrue(stub.last("/").body.contains("\"TemplateParamSet\":[\"1\",\"2\",\"8888\"]"),
                "params 是 HashMap，顺序必须按 key 定死: " + stub.last("/").body);

        Message explicit = sms("13800138000");
        explicit.getParams().put("templateParamSet", "2,1");
        assertTrue(sender.send(explicit).isSuccess());
        assertTrue(stub.last("/").body.contains("\"TemplateParamSet\":[\"2\",\"1\"]"),
                "param(\"templateParamSet\") 显式顺序优先: " + stub.last("/").body);
    }

    @Test
    void authFailureBodyOn403MapsToProviderCodeAndIsNotRetried() {
        props.channel(Channels.SMS).setRetries(2);
        stub.enqueue("/", 403, "{\"Response\":{\"Error\":{\"Code\":\"AuthFailure.SignatureFailure\","
                + "\"Message\":\"The provided credentials could not be validated.\"},"
                + "\"RequestId\":\"req-x\"}}");

        MessageSendResult r = sender.send(sms("13800138000"));
        assertFalse(r.isSuccess());
        assertEquals("TENCENT_SMS_AuthFailure.SignatureFailure", r.getErrorCode());
        assertTrue(r.getErrorMessage().contains("credentials"), "errmsg 要带出来供投递日志排障");
        assertEquals(1, stub.countFor("/"), "4xx 业务/鉴权拒绝不是网络抖动，HTTP 层不许重试");
    }

    @Test
    void perNumberRejectionFailsEvenOn200() {
        stub.enqueue("/", 200, "{\"Response\":{\"SendStatusSet\":[{"
                + "\"Code\":\"FailedOperation.PostpaidDayLimitExceeded\",\"Message\":\"余额不足\"},"
                + "{\"Code\":\"Ok\",\"Message\":\"send success\"}],\"RequestId\":\"req-y\"}}");

        MessageSendResult r = sender.send(sms("13800138000"));
        assertFalse(r.isSuccess(), "第一个号码被拒即整条失败，不能被同批的 Ok 掩盖");
        assertEquals("TENCENT_SMS_FailedOperation.PostpaidDayLimitExceeded", r.getErrorCode());
        assertEquals("余额不足", r.getErrorMessage());
    }

    @Test
    void unparseableBodyFailsAsBadResponse() {
        stub.enqueue("/", 200, "not json at all");
        MessageSendResult r = sender.send(sms("13800138000"));
        assertFalse(r.isSuccess());
        assertEquals("TENCENT_SMS_BAD_RESPONSE", r.getErrorCode());
    }

    @Test
    void missingSdkAppIdMeansNotReady() {
        props.channel(Channels.SMS).setSdkAppId(null);
        assertFalse(sender.ready());
        // 只缺一个凭据段同样不算 ready
        ChannelsProperties.ChannelCfg half = new ChannelsProperties.ChannelCfg();
        half.setSdkAppId(SDK_APP_ID);
        half.setAccessKeyId(SID);
        props.put(Channels.SMS, half);
        assertFalse(sender.ready(), "SecretId 有、SecretKey 没给也不行");
    }

    @Test
    void providerNameAndChannelAreTheContract() {
        assertEquals("tencent", sender.provider());
        assertEquals(Channels.SMS, sender.channel());
    }
}
