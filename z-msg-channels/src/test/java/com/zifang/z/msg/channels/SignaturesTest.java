package com.zifang.z.msg.channels;

import com.zifang.z.msg.channels.util.Signatures;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 签名算法的"固定输入 → 固定期望值"向量。
 * <p>
 * 期望值不是跑一遍被测代码抄下来的，而是用离线独立参考实现（python3 hmac/hashlib +
 * urllib.parse.quote(safe='~')，本地预计算，无需联网）算好后钉在常量里：
 * <pre>
 * dingtalk: hmac_sha256(key=SECding_secret_vector, msg="1690000000000\nSECding_secret_vector")
 * feishu  : hmac_sha256(key="1690000000000\nfeishu_secret_vector", msg=b"")
 * aliyun  : RPC 风格 HmacSHA1(key="aksecret_vector&", msg=stringToSign)
 * </pre>
 * 被测实现与参考实现任何一步（密钥取法、被签数据、编码替换、Base64 变体）跑偏，这些常量立刻红。
 */
class SignaturesTest {

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
