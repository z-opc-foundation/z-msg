package com.zifang.z.msg.channels.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 测试侧独立参考签名实现：与生产代码 {@code Signatures} 分开手写，用于在 HTTP 形状测试里
 * 对"被捕获的 timestamp"重算签名作对照；纯算法本身另由 {@code SignaturesTest} 用
 * 离线（python3 hmac/openssl）预制的固定输入→固定期望值向量钉死，避免"被测函数自证"。
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
