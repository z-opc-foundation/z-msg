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
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阿里云短信（RPC/HmacSHA1 排序签名）。nonce 与 Timestamp 注入固定值，
 * 于是整条 query 与签名都是"固定输入 → 固定期望值"：期望串离线（python3）算好钉在这里。
 */
class AliyunSmsSenderTest {

    private static final String AK = "testid";
    private static final String SK = "aksecret_vector";
    private static final String NONCE = "fixed-nonce-0001";
    private static final String TS = "2023-07-22T08:00:00Z";

    private StubServer stub;
    private ChannelsProperties props;
    private AliyunSmsSender sender;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubServer();
        props = new ChannelsProperties();
        ChannelsProperties.ChannelCfg cfg = new ChannelsProperties.ChannelCfg();
        cfg.setBaseUrl(stub.baseUrl());
        cfg.setAccessKeyId(AK);
        cfg.setAccessKeySecret(SK);
        cfg.setSignName("z-opc测试");
        cfg.setTemplateCode("SMS_123456");
        cfg.setRegion("cn-hangzhou");
        props.put(Channels.SMS, cfg);
        sender = new AliyunSmsSender(props, new SimpleHttpClient(), () -> NONCE, () -> TS);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static Message sms() {
        return Message.builder().channel(Channels.SMS).receiver("13800138000")
                .bizType("REGISTER").param("code", "8888").build();
    }

    @Test
    void canonicalQueryAndSignatureEqualOfflineVector() {
        stub.enqueue("/", 200, "{\"Code\":\"OK\",\"RequestId\":\"req-1\",\"BizId\":\"biz-42\"}");

        MessageSendResult r = sender.send(sms());
        assertTrue(r.isSuccess(), "正例对照：Code=OK 成功");
        assertEquals("biz-42", r.getProviderMessageId());

        StubServer.Recorded req = stub.last("/");
        assertEquals("POST", req.method);
        // 原文对照（未解码的 rawQuery 去掉 Signature 段）：钉"参数排序 + 百分号编码"两件事
        String raw = req.rawQuery;
        int idx = raw.lastIndexOf("&Signature=");
        assertTrue(idx > 0, "Signature 必须随 query 上送");
        assertEquals("AccessKeyId=testid&Action=SendSms&Format=JSON&PhoneNumbers=13800138000"
                        + "&RegionId=cn-hangzhou&SignName=z-opc%E6%B5%8B%E8%AF%95"
                        + "&SignatureMethod=HMAC-SHA1&SignatureNonce=fixed-nonce-0001"
                        + "&SignatureVersion=1.0&TemplateCode=SMS_123456"
                        + "&TemplateParam=%7B%22code%22%3A%228888%22%7D"
                        + "&Timestamp=2023-07-22T08%3A00%3A00Z&Version=2017-05-25",
                raw.substring(0, idx));
        assertEquals("Td0kLhFnawX2kJP1Dgk3GETY5qg=", java.net.URLDecoder.decode(
                raw.substring(idx + "&Signature=".length()), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void signatureRecomputedIndependentlyFromCapturedCanonical() {
        stub.enqueue("/", 200, "{\"Code\":\"OK\"}");
        sender.send(sms());

        StubServer.Recorded req = stub.last("/");
        String raw = req.rawQuery;
        String canonical = raw.substring(0, raw.lastIndexOf("&Signature="));
        String expected = TestSigns.aliyunSign(SK, "POST&%2F&" + TestSigns.percentEncode(canonical));
        String actual = req.query().get("Signature");
        assertEquals(expected, actual, "签名应等于独立实现对捕获 canonical 的重算");
    }

    @Test
    void businessRejectionFailsWithProviderMessageNoException() {
        stub.enqueue("/", 200, "{\"Code\":\"OK\",\"RequestId\":\"r0\"}");
        stub.enqueue("/", 200,
                "{\"Code\":\"isv.SMS_SIGNATURE_ILLEGAL\",\"Message\":\"invalid signature\",\"RequestId\":\"r1\"}");

        assertTrue(sender.send(sms()).isSuccess(), "正例对照：Code=OK 成功");

        MessageSendResult r = sender.send(sms());
        assertFalse(r.isSuccess());
        assertEquals("ALIYUN_SMS_isv.SMS_SIGNATURE_ILLEGAL", r.getErrorCode());
        assertTrue(r.getErrorMessage().contains("invalid signature"));
        assertEquals(2, stub.countFor("/"));
    }

    @Test
    void perMessageSignAndTemplateOverrideConfig() {
        stub.enqueue("/", 200, "{\"Code\":\"OK\"}");
        Message m = sms();
        m.getParams().put("signName", "另一签名");
        m.getParams().put("templateCode", "SMS_999");

        assertTrue(sender.send(m).isSuccess(), "正例对照");

        Map<String, String> q = new TreeMap<>(stub.last("/").query());
        assertEquals("另一签名", q.get("SignName"));
        assertEquals("SMS_999", q.get("TemplateCode"));
        assertFalse(q.containsKey("SignatureNonce") && q.get("SignatureNonce").isEmpty());
        // 保留字不能混进 TemplateParam JSON
        String tp = q.get("TemplateParam");
        assertTrue(tp.contains("8888") && !tp.contains("signName") && !tp.contains("SMS_999"),
                "TemplateParam 只含模板变量: " + tp);
    }

    @Test
    void missingKeysMeansNotReady() {
        props.put(Channels.SMS, new ChannelsProperties.ChannelCfg());
        assertFalse(sender.ready());
    }
}
