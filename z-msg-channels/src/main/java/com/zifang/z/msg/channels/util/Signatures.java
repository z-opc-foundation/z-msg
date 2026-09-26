package com.zifang.z.msg.channels.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

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

    /**
     * 腾讯云 API 3.0 签名（TC3-HMAC-SHA256）。
     * <p>
     * 规范形状（四步，任何一步偏一格服务端就是 AuthFailure.SignatureFailure）：
     * <ol>
     *   <li>canonical request =
     *       {@code POST\n<uri>\n<query>\n<小写 key 字典序的 header 行，每行以 \n 结尾>\n\n<signedHeaders>\n<Hex(SHA256(payload))>}
     *       —— 注意 header 段自带结尾换行，所以它与 signedHeaders 之间还有一个空行；</li>
     *   <li>string to sign = {@code TC3-HMAC-SHA256\n<timestampSeconds>\n<date(UTC yyyy-MM-dd)>\n<Hex(SHA256(canonical))>}；</li>
     *   <li>派生密钥链 = {@code HMAC(key="TC3"+secretKey, data=date) → service → "tc3_request"}；</li>
     *   <li>signature = {@code HexLowercase(HMAC(kSigning, stringToSign))}（是十六进制，不是 Base64）。</li>
     * </ol>
     * 只有 {@code signedHeaders} 列出的头参与签名，其余头（如 X-TC-*）可以照常上送。
     *
     * @param service       服务名，短信是 {@code sms}；同时出现在 Credential 作用域里
     * @param headers       参与签名的头，key 大小写不限（内部统一转小写），值按规范折叠空白
     * @param canonicalUri  已按规范百分号编码的 URI，例如 {@code /}
     * @param canonicalQuery 已按规范编码的 query，无参数传空串
     */
    public static Tc3Signed tc3Sign(String secretId, String secretKey, String service,
                                    String httpMethod, String canonicalUri, String canonicalQuery,
                                    Map<String, String> headers, long timestampSeconds, String payload) {
        Map<String, String> sorted = new TreeMap<>();
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                sorted.put(collapseSpaces(e.getKey().toLowerCase(Locale.ROOT)),
                        collapseSpaces(e.getValue() == null ? "" : e.getValue()));
            }
        }
        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signedHeaders = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            canonicalHeaders.append(e.getKey()).append(':').append(e.getValue()).append('\n');
            if (signedHeaders.length() > 0) {
                signedHeaders.append(';');
            }
            signedHeaders.append(e.getKey());
        }
        String hashedPayload = sha256Hex(payload == null ? "" : payload);
        String canonicalRequest = httpMethod.toUpperCase(Locale.ROOT) + "\n" + canonicalUri + "\n"
                + (canonicalQuery == null ? "" : canonicalQuery) + "\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + hashedPayload;
        String date = utcDate(timestampSeconds);
        String stringToSign = "TC3-HMAC-SHA256\n" + timestampSeconds + "\n" + date + "\n"
                + sha256Hex(canonicalRequest);
        byte[] kDate = hmac("HmacSHA256", ("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] kService = hmac("HmacSHA256", kDate, service);
        byte[] kSigning = hmac("HmacSHA256", kService, "tc3_request");
        String signature = hex(hmac("HmacSHA256", kSigning, stringToSign));
        String authorization = "TC3-HMAC-SHA256 Credential=" + secretId + "/" + date + "/"
                + service + "/tc3_request, SignedHeaders=" + signedHeaders + ", Signature=" + signature;
        return new Tc3Signed(canonicalRequest, stringToSign, signedHeaders.toString(), signature, authorization);
    }

    /** UTC 日期串（TC3 的 date 既进 string to sign，也进 Credential 作用域）。 */
    public static String utcDate(long epochSeconds) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new java.util.Date(epochSeconds * 1000L));
    }

    /** HexLowercase(SHA256(text))，UTF-8 字节。 */
    public static String sha256Hex(String text) {
        try {
            return hex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String hex(byte[] raw) {
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** 规范要求的 header 值折叠：去首尾空白、内部连续空白压成一个空格。 */
    private static String collapseSpaces(String v) {
        return v.trim().replaceAll("\\s+", " ");
    }

    /** 固定结构：canonical request / 待签串 / SignedHeaders / 签名 / 完整 Authorization。 */
    public static final class Tc3Signed {
        private final String canonicalRequest;
        private final String stringToSign;
        private final String signedHeaders;
        private final String signature;
        private final String authorization;

        Tc3Signed(String canonicalRequest, String stringToSign, String signedHeaders,
                  String signature, String authorization) {
            this.canonicalRequest = canonicalRequest;
            this.stringToSign = stringToSign;
            this.signedHeaders = signedHeaders;
            this.signature = signature;
            this.authorization = authorization;
        }

        public String getCanonicalRequest() {
            return canonicalRequest;
        }

        public String getStringToSign() {
            return stringToSign;
        }

        public String getSignedHeaders() {
            return signedHeaders;
        }

        public String getSignature() {
            return signature;
        }

        public String getAuthorization() {
            return authorization;
        }
    }

    /**
     * Basic 认证头值：{@code Basic Base64(user + ":" + password)}（UTF-8 字节），极光用它。
     * 返回整串（含 "Basic " 前缀），调用方直接塞进 Authorization。
     */
    public static String basicAuth(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
