package com.zifang.z.msg.channels.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 各家渠道的签名原语（HmacSHA256/HmacSHA1/Base64/百分号编码）。
 * <p>
 * 独立成类的理由：测试用**另一份实现**（测试目录里的独立参考实现 + 离线预制的
 * 固定期望值）对照这里的输出，而不是调被测函数自己当期望值。
 */
public final class Signatures {

    private Signatures() {
    }

    /** HmacSHA1 → Base64（阿里云 RPC 签名用）。 */
    public static String hmacSha1Base64(String key, String data) {
        byte[] raw = hmac("HmacSHA1", key.getBytes(StandardCharsets.UTF_8),
                data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(raw);
    }

    public static byte[] hmac(String algorithm, byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException(algorithm + " 计算失败", e);
        }
    }

    /**
     * 钉钉规范：urlencode(Base64(HmacSHA256(key=secret, data=timestamp + "\n" + secret)))。
     * 返回已百分号编码、可直接拼进 query 的串。
     */
    public static String dingTalkSign(String secret, long timestampMillis) {
        return percentEncode(hmacSha256Base64(secret, timestampMillis + "\n" + secret));
    }

    /**
     * 飞书规范：Base64(HmacSHA256(key = timestamp + "\n" + secret, data = ""))，
     * 密钥是 "timestamp 换行 secret" 整串，被签名数据是空串。
     * 返回未编码串（飞书把 sign 放 body JSON，不走 URL）。
     */
    public static String feishuSign(String secret, long timestampSeconds) {
        return hmacSha256Base64(timestampSeconds + "\n" + secret, "");
    }

    /** Base64(HmacSHA256(key, data))，UTF-8 字节，标准 Base64 不编码。 */
    public static String hmacSha256Base64(String key, String data) {
        byte[] raw = hmac("HmacSHA256", key.getBytes(StandardCharsets.UTF_8),
                data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(raw);
    }

    /**
     * 阿里云 RPC 风格百分号编码：在标准 URLEncoder 基础上
     * {@code + → %20}、{@code * → %2A}、{@code %7E → ~}（官方签名文档明确要求的三处差异）。
     */
    public static String percentEncode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8")
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 不可用", e);
        }
    }

    /**
     * 阿里云 RPC（POP）签名：参数按 key 字典序排列 → 百分号编码拼接 →
     * {@code METHOD&%2F&encode(canon)} 作待签串 → HmacSHA1(key = secret + "&amp;") → Base64。
     * <p>
     * 返回三元组（canonicalQuery / stringToSign / signature），前两项供测试与排障逐段对照。
     */
    public static AliyunRpcSigned aliyunRpcSign(String httpMethod, Map<String, String> params,
                                                String accessKeySecret) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder canon = new StringBuilder();
        for (String k : keys) {
            if (canon.length() > 0) {
                canon.append('&');
            }
            canon.append(percentEncode(k)).append('=').append(percentEncode(params.get(k)));
        }
        String stringToSign = httpMethod.toUpperCase() + "&%2F&" + percentEncode(canon.toString());
        String signature = hmacSha1Base64(accessKeySecret + "&", stringToSign);
        return new AliyunRpcSigned(canon.toString(), stringToSign, signature);
    }

    /** 固定结构：canonical query / 待签串 / 签名，避免调用方按位置取错。 */
    public static final class AliyunRpcSigned {
        private final String canonicalQuery;
        private final String stringToSign;
        private final String signature;

        AliyunRpcSigned(String canonicalQuery, String stringToSign, String signature) {
            this.canonicalQuery = canonicalQuery;
            this.stringToSign = stringToSign;
            this.signature = signature;
        }

        public String getCanonicalQuery() {
            return canonicalQuery;
        }

        public String getStringToSign() {
            return stringToSign;
        }

        public String getSignature() {
            return signature;
        }
    }
}
