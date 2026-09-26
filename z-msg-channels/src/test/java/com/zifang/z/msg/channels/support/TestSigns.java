package com.zifang.z.msg.channels.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

/**
 * 测试侧独立参考签名实现：与生产代码 {@code Signatures} 分开手写，用于在 HTTP 形状测试里
 * 对"被捕获的 timestamp"重算签名作对照；纯算法本身另由 {@code SignaturesTest} 用
 * 离线（python3 hmac/openssl）预制的固定输入→固定期望值向量钉死，避免"被测函数自证"。
 * <p>
 * 本类的写法刻意与生产实现走不同的路（{@code java.time} 而非 SimpleDateFormat、
 * 腾讯云签名把 content-type/host 作显式参数而非 Map），这样两边同时手滑的概率更低。
 */
public final class TestSigns {

    private TestSigns() {
    }

    /** 钉钉：Base64(HmacSHA256(secret, ts + "\n" + secret))，未再做 URL 编码（stub 侧 query 已解码） */
    public static String dingTalkSignRaw(String secret, String timestampMillis) {
        return b64("HmacSHA256", secret.getBytes(StandardCharsets.UTF_8),
                (timestampMillis + "\n" + secret).getBytes(StandardCharsets.UTF_8));
    }

    /** 钉钉：urlencode(Base64(HmacSHA256(secret, ts + "\n" + secret))) */
    public static String dingTalkSign(String secret, String timestampMillis) {
        return formEncode(dingTalkSignRaw(secret, timestampMillis));
    }

    /** 飞书：Base64(HmacSHA256(key=ts + "\n" + secret, data="")) */
    public static String feishuSign(String secret, String timestampSeconds) {
        return b64("HmacSHA256", (timestampSeconds + "\n" + secret).getBytes(StandardCharsets.UTF_8),
                new byte[0]);
    }

    /** 阿里云 RPC percentEncode（与规范一致：+→%20, *→%2A, %7E→~） */
    public static String percentEncode(String v) {
        return formEncode(v).replace("*", "%2A").replace("%7E", "~");
    }

    /** 阿里云待签串 → Base64(HmacSHA1(secret + "&", stringToSign)) */
    public static String aliyunSign(String accessKeySecret, String stringToSign) {
        return b64("HmacSHA1", (accessKeySecret + "&").getBytes(StandardCharsets.UTF_8),
                stringToSign.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 腾讯云 TC3-HMAC-SHA256 的完整 Authorization 头值（独立手写）。
     * <p>
     * 刻意只吃"线上真正发出去的那几个字节"：content-type 与 host 由调用方从 stub 捕获的
     * 请求头原样喂进来，payload 是捕获的请求体原文 —— 于是重算出的签名一致即证明
     * "签的就是发的"，而不是"签了另一个字符串、恰好也没报错"。
     */
    public static String tc3Authorization(String secretId, String secretKey, String service,
                                          String contentType, String host, String payload,
                                          long timestampSeconds) {
        String canonicalHeaders = "content-type:" + contentType + "\n" + "host:" + host + "\n";
        // 方法\nURI\nQuery\n(每个 header 一行、自带结尾换行)\n空行\nSignedHeaders\nHexSha256(payload)
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n"
                + "content-type;host\n" + sha256Hex(payload);
        String date = Instant.ofEpochSecond(timestampSeconds).atZone(ZoneOffset.UTC)
                .toLocalDate().toString();
        String stringToSign = "TC3-HMAC-SHA256\n" + timestampSeconds + "\n" + date + "\n"
                + sha256Hex(canonicalRequest);
        byte[] kDate = hmac(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] kService = hmac(kDate, service);
        byte[] kSigning = hmac(kService, "tc3_request");
        String signature = hex(hmac(kSigning, stringToSign));
        return "TC3-HMAC-SHA256 Credential=" + secretId + "/" + date + "/" + service
                + "/tc3_request, SignedHeaders=content-type;host, Signature=" + signature;
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256Hex(String v) {
        try {
            byte[] raw = MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] raw) {
        StringBuilder sb = new StringBuilder();
        for (byte b : raw) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String b64(String alg, byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(alg);
            mac.init(new SecretKeySpec(key, alg));
            return Base64.getEncoder().encodeToString(mac.doFinal(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String formEncode(String v) {
        try {
            return URLEncoder.encode(v, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
