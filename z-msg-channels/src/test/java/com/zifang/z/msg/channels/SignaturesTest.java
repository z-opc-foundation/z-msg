package com.zifang.z.msg.channels;

import com.zifang.z.msg.channels.support.TestSigns;
import com.zifang.z.msg.channels.util.Signatures;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 签名算法的"固定输入 → 固定期望值"向量。
 * <p>
 * 期望值不是跑一遍被测代码抄下来的，而是用离线独立参考实现（python3 hmac/hashlib +
 * urllib.parse.quote(safe='~')，本地预计算，无需联网）算好后钉在常量里：
 * <pre>
 * dingtalk: hmac_sha256(key=SECding_secret_vector, msg="1690000000000\nSECding_secret_vector")
 * feishu  : hmac_sha256(key="1690000000000\nfeishu_secret_vector", msg=b"")
 * aliyun  : RPC 风格 HmacSHA1(key="aksecret_vector&", msg=stringToSign)
 * tencent : TC3-HMAC-SHA256，canonical/stringToSign/signature 三段逐层钉（见 ~/.cache 的 tc3_kat.py）
 * basic   : base64("123456:masterSecretForTest") / base64("apikeyJPUSH2200:公司")（UTF-8）
 * </pre>
 * 被测实现与参考实现任何一步（密钥取法、被签数据、编码替换、Base64 变体）跑偏，这些常量立刻红。
 */
class SignaturesTest {

    /** 腾讯云文档示例的那组占位凭据（尾部 EXAMPLE，非活凭据），拿来当固定输入。 */
    private static final String TC3_SECRET_ID = "AKID" + "z8krbsJ5yKBZQpn74WFkmLPx3EXAMPLE";
    private static final String TC3_SECRET_KEY = "Gu5t9xGARNpq86cd98joQYCN3EXAMPLE";
    private static final String TC3_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String TC3_HOST = "sms.tencentcloudapi.com";
    private static final long TC3_TS = 1551113065L;
    private static final String TC3_PAYLOAD = "{\"PhoneNumberSet\":[\"+8613800138000\"],"
            + "\"SmsSdkAppId\":\"1400009100\",\"SignName\":\"z-opc\",\"TemplateId\":\"1648\","
            + "\"TemplateParamSet\":[\"8888\"]}";
    private static final String TC3_AUTHORIZATION = "TC3-HMAC-SHA256 "
            + "Credential=" + TC3_SECRET_ID + "/2019-02-25/sms/tc3_request, "
            + "SignedHeaders=content-type;host, "
            + "Signature=1fc627da29b445a9726d7d2b60650d6f5716c1d3da239bbdc8e85025ca357979";

    @Test
    void dingTalkSignMatchesOfflineVector() {
        String sign = Signatures.dingTalkSign("SECding_secret_vector", 1690000000000L);
        assertEquals("3djNz4xA45loCDffJUfAL%2FyDHxWthmbfYd17%2BZne2Fc%3D", sign);
    }

    @Test
    void feishuSignMatchesOfflineVector() {
        String sign = Signatures.feishuSign("feishu_secret_vector", 1690000000000L);
        assertEquals("iYA5oyx/WtKZMKNpz6ZXO1NWGOg/6aOshD+F3BzUi2g=", sign);
    }

    /**
     * TC3 四层逐段钉死：canonical request（含"header 段自带结尾换行 + 与 SignedHeaders 之间
     * 还有一个空行"这个最容易少一个 \n 的形状）、string to sign、签名、完整 Authorization。
     * 输入里的 header key 故意写成大写，钉"key 归一为小写、值不变"这条规范细节。
     */
    @Test
    void tc3CanonicalStringToSignAndSignatureMatchOfflineVector() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", TC3_CONTENT_TYPE);
        headers.put("Host", TC3_HOST);

        Signatures.Tc3Signed signed = Signatures.tc3Sign(TC3_SECRET_ID, TC3_SECRET_KEY, "sms",
                "POST", "/", "", headers, TC3_TS, TC3_PAYLOAD);

        assertEquals("POST\n/\n\ncontent-type:" + TC3_CONTENT_TYPE + "\nhost:" + TC3_HOST
                        + "\n\ncontent-type;host\n"
                        + "ae2071b32cdc8547e41e57c66da171618309f3696a5ef378d62b4c4bd74d2181",
                signed.getCanonicalRequest());
        assertEquals("TC3-HMAC-SHA256\n1551113065\n2019-02-25\n"
                        + "3ad9d10f2215be3382ba9636923d009d6873f8671fadd78d120e90e35291081b",
                signed.getStringToSign());
        assertEquals("content-type;host", signed.getSignedHeaders());
        assertEquals(TC3_AUTHORIZATION, signed.getAuthorization());
    }

    /**
     * 两份实现（生产的 {@link Signatures} 与测试侧手写的 {@link TestSigns}，日期取法、
     * 拼串方式都不同）必须从同一组输入得出同一个 Authorization —— 用来排除"两边同错"之外的
     * 单边手滑；真正的外部真值是上面那条 python3 向量。
     */
    @Test
    void testSideTc3ImplementationAgreesWithProductionOne() {
        assertEquals(TC3_AUTHORIZATION, TestSigns.tc3Authorization(TC3_SECRET_ID, TC3_SECRET_KEY,
                "sms", TC3_CONTENT_TYPE, TC3_HOST, TC3_PAYLOAD, TC3_TS));
        // 阳性对照：host 换一个字符必须换签名（否则"两边一致"可能是"签名谁都没读 host"）
        assertFalse(TC3_AUTHORIZATION.equals(TestSigns.tc3Authorization(TC3_SECRET_ID, TC3_SECRET_KEY,
                "sms", TC3_CONTENT_TYPE, "sms.tencentcloudapi.cn", TC3_PAYLOAD, TC3_TS)));
    }

    @Test
    void basicAuthIsBase64OfColonJoinedUtf8() {
        assertEquals("Basic MTIzNDU2Om1hc3RlclNlY3JldEZvclRlc3Q=",
                Signatures.basicAuth("123456", "masterSecretForTest"));
        // 中文口令：钉"按 UTF-8 取字节"，不是平台默认编码
        assertEquals("Basic YXBpa2V5SlBVU0gyMjAwOuWFrOWPuA==",
                Signatures.basicAuth("apikeyJPUSH2200", "公司"));
    }

    @Test
    void aliyunRpcCanonicalQueryAndSignatureMatchOfflineVector() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("AccessKeyId", "testid");
        params.put("Action", "SendSms");
        params.put("Format", "JSON");
        params.put("PhoneNumbers", "13800138000");
        params.put("RegionId", "cn-hangzhou");
        params.put("SignName", "z-opc测试");
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureNonce", "fixed-nonce-0001");
        params.put("SignatureVersion", "1.0");
        params.put("TemplateCode", "SMS_123456");
        params.put("TemplateParam", "{\"code\":\"8888\"}");
        params.put("Timestamp", "2023-07-22T08:00:00Z");
        params.put("Version", "2017-05-25");

        Signatures.AliyunRpcSigned signed = Signatures.aliyunRpcSign("POST", params, "aksecret_vector");

        assertEquals("AccessKeyId=testid&Action=SendSms&Format=JSON&PhoneNumbers=13800138000"
                        + "&RegionId=cn-hangzhou&SignName=z-opc%E6%B5%8B%E8%AF%95"
                        + "&SignatureMethod=HMAC-SHA1&SignatureNonce=fixed-nonce-0001"
                        + "&SignatureVersion=1.0&TemplateCode=SMS_123456"
                        + "&TemplateParam=%7B%22code%22%3A%228888%22%7D"
                        + "&Timestamp=2023-07-22T08%3A00%3A00Z&Version=2017-05-25",
                signed.getCanonicalQuery());
        assertEquals("POST&%2F&AccessKeyId%3Dtestid%26Action%3DSendSms%26Format%3DJSON"
                        + "%26PhoneNumbers%3D13800138000%26RegionId%3Dcn-hangzhou"
                        + "%26SignName%3Dz-opc%25E6%25B5%258B%25E8%25AF%2595"
                        + "%26SignatureMethod%3DHMAC-SHA1%26SignatureNonce%3Dfixed-nonce-0001"
                        + "%26SignatureVersion%3D1.0%26TemplateCode%3DSMS_123456"
                        + "%26TemplateParam%3D%257B%2522code%2522%253A%25228888%2522%257D"
                        + "%26Timestamp%3D2023-07-22T08%253A00%253A00Z%26Version%3D2017-05-25",
                signed.getStringToSign());
        assertEquals("Td0kLhFnawX2kJP1Dgk3GETY5qg=", signed.getSignature());
    }

    @Test
    void percentEncodeAppliesAliyunThreeSpecialSubstitutions() {
        // 空格 → %20（不是 +）、* → %2A、~ 保持原样（URLEncoder 会编成 %7E）
        assertEquals("a%20%E6%96%87", Signatures.percentEncode("a 文"));
        assertEquals("%2A", Signatures.percentEncode("*"));
        assertEquals("~", Signatures.percentEncode("~"));
        assertEquals("%3A", Signatures.percentEncode(":"));
    }
}
