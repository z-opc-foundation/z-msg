package com.zifang.z.msg.channels.provider;

import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.channels.config.ChannelsProperties;
import com.zifang.z.msg.channels.http.HttpResponse;
import com.zifang.z.msg.channels.http.SimpleHttpClient;
import com.zifang.z.msg.channels.util.Signatures;
import com.zifang.z.msg.core.json.MsgJson;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * 腾讯云短信（{@code sms.tencentcloudapi.com}，SendSms / API 2021-01-11，TC3-HMAC-SHA256 签名）。
 * <p>
 * 官方形状：POST 到根路径，参数全在 JSON body
 * （{@code PhoneNumberSet / SmsSdkAppId / SignName / TemplateId / TemplateParamSet}），
 * 公共参数走 {@code X-TC-Action / X-TC-Version / X-TC-Region / X-TC-Timestamp} 头，
 * 鉴权走 {@code Authorization: TC3-HMAC-SHA256 Credential=.../sms/tc3_request,
 * SignedHeaders=content-type;host, Signature=<hex>}。
 * 只有 {@code content-type} 与 {@code host} 两个头参与签名，所以这两个值**必须与线上真正发出去的一致**：
 * content-type 用本类常量，host 由最终 URL 现推（{@link #hostOf}），而不是再写一遍字面量。
 * <p>
 * 响应形状 {@code {"Response":{"SendStatusSet":[{"Code":"Ok","Message":"send success",
 * "SerialNumber":"..."}],"RequestId":"..."}}}：
 * 逐号码的 Code 不是 Ok 即业务拒绝；鉴权/参数错误则是 {@code Response.Error.{Code,Message}}，
 * 这类回包可能配着 4xx 状态码一起来，所以先按 body 里的 Error 翻译，翻不出才退回 HTTP 层。
 * <p>
 * timestamp 通过构造参数注入（生产给时钟、测试给固定值），于是"固定输入 → 固定签名"可钉。
 * <p>
 * 两点与阿里云不同、易踩：
 * <ul>
 *   <li>{@code TemplateParamSet} 是**按位置**取的数组，而 {@code Message.params} 是 HashMap
 *       （迭代顺序不稳）——本类按 key 字典序取值，模板变量必须靠命名可排序或只用一个变量；
 *       需要严格顺序时改用 {@code param("templateParamSet")} 逗号分隔显式给；</li>
 *   <li>号码要 E.164（{@code +8613800138000}）：裸号码自动补 {@code +86}，补不出 E.164 形状就
 *       本地 fail，不发那次必然被拒的请求。</li>
 * </ul>
 */
public class TencentSmsSender extends AbstractHttpChannelSender {

    public static final String PROVIDER = "tencent";
    private static final String DEFAULT_BASE = "https://sms.tencentcloudapi.com";
    private static final String API_VERSION = "2021-01-11";
    private static final String SERVICE = "sms";
    /** 签进去的就是它：与 {@link Signatures#tc3Sign} 的 canonical header 逐字节同源 */
    static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String DEFAULT_REGION = "ap-guangzhou";
    /** E.164：+ 开头、总位数 6—15（腾讯云文档的"号码格式"约束） */
    private static final String E164 = "\\+\\d{6,15}";

    private final Supplier<String> timestampSupplier;

    public TencentSmsSender(ChannelsProperties properties, SimpleHttpClient http) {
        this(properties, http, () -> String.valueOf(System.currentTimeMillis() / 1000L));
    }

    public TencentSmsSender(ChannelsProperties properties, SimpleHttpClient http,
                            Supplier<String> timestampSupplier) {
        super(properties, http, Channels.SMS, PROVIDER);
        this.timestampSupplier = timestampSupplier;
    }

    @Override
    protected boolean configured() {
        ChannelsProperties.ChannelCfg cfg = cfg();
        return trimToNull(cfg.getAccessKeyId()) != null
                && trimToNull(cfg.getAccessKeySecret()) != null
                && trimToNull(cfg.getSdkAppId()) != null;
    }

    @Override
    protected MessageSendResult doSend(Message message) {
        ChannelsProperties.ChannelCfg cfg = cfg();
        String secretId = trimToNull(cfg.getAccessKeyId());
        String secretKey = trimToNull(cfg.getAccessKeySecret());
        if (secretId == null || secretKey == null) {
            return MessageSendResult.fail(provider(), "PROVIDER_NOT_CONFIGURED",
                    "z-msg.channel.sms.access-key-id / access-key-secret 未配置（腾讯云侧即 SecretId / SecretKey）");
        }
        String sdkAppId = trimToNull(cfg.getSdkAppId());
        if (sdkAppId == null) {
            return MessageSendResult.fail(provider(), "PROVIDER_NOT_CONFIGURED",
                    "z-msg.channel.sms.sdk-app-id 未配置（SmsSdkAppId，应用侧 1400xxxxxxx）");
        }
        String signName = message.param("signName", null);
        if (trimToNull(signName) == null) {
            signName = trimToNull(cfg.getSignName());
        }
        if (signName == null) {
            return MessageSendResult.fail(provider(), "PROVIDER_NOT_CONFIGURED",
                    "短信签名 signName 未配置（param 或 z-msg.channel.sms.sign-name）");
        }
        String templateId = message.param("templateCode", null);
        if (trimToNull(templateId) == null) {
            templateId = trimToNull(cfg.getTemplateCode());
        }
        if (templateId == null) {
            return MessageSendResult.fail(provider(), "TEMPLATE_NOT_FOUND", "短信模板 TemplateId 为空");
        }
        String phone;
        try {
            phone = e164(message.getReceiver());
        } catch (IllegalArgumentException e) {
            return MessageSendResult.fail(provider(), "INVALID_RECEIVER", e.getMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("PhoneNumberSet", Collections.singletonList(phone));
        body.put("SmsSdkAppId", sdkAppId);
        body.put("SignName", signName);
        body.put("TemplateId", templateId);
        List<String> templateParams = templateParamSet(message);
        if (!templateParams.isEmpty()) {
            body.put("TemplateParamSet", templateParams);
        }
        String payload = MsgJson.toJson(body);

        String url = buildUrl(cfg.getUrl(), DEFAULT_BASE, "/");
        String ts = timestampSupplier.get();
        Map<String, String> signed = new LinkedHashMap<>();
        signed.put("content-type", CONTENT_TYPE);
        signed.put("host", hostOf(url));
        Signatures.Tc3Signed signedReq = Signatures.tc3Sign(secretId, secretKey, SERVICE,
                "POST", canonicalUriOf(url), "", signed, Long.parseLong(ts.trim()), payload);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", CONTENT_TYPE);
        headers.put("Accept", "application/json");
        headers.put("X-TC-Action", "SendSms");
        headers.put("X-TC-Version", API_VERSION);
        headers.put("X-TC-Region", trimToNull(cfg.getRegion()) == null
                ? DEFAULT_REGION : cfg.getRegion().trim());
        headers.put("X-TC-Timestamp", ts);
        headers.put("Authorization", signedReq.getAuthorization());

        HttpResponse resp = execute("POST", url, headers, payload);
        return interpret(resp, message);
    }

    /** 先按回包里的 {@code Response.Error}/{@code SendStatusSet} 翻译，翻不出才退回 HTTP 层。 */
    private MessageSendResult interpret(HttpResponse resp, Message message) {
        Map<String, Object> root = MsgJson.toMap(resp.getBody());
        Map<String, Object> r = asMap(root == null ? null : root.get("Response"));
        if (r != null) {
            Map<String, Object> error = asMap(r.get("Error"));
            if (error != null) {
                return MessageSendResult.fail(provider(), "TENCENT_SMS_" + orUnknown(error.get("Code")),
                        str(error.get("Message")));
            }
            List<Object> statusSet = asList(r.get("SendStatusSet"));
            if (statusSet != null && !statusSet.isEmpty()) {
                Map<String, Object> first = asMap(statusSet.get(0));
                String code = first == null ? null : str(first.get("Code"));
                String detail = first == null ? null : str(first.get("Message"));
                if ("Ok".equalsIgnoreCase(code)) {
                    String serial = first.get("SerialNumber") == null
                            ? str(r.get("RequestId")) : str(first.get("SerialNumber"));
                    log.info("[z-msg-tencent-sms] OK msgId={} serial={} requestId={}",
                            message.getMsgId(), serial, r.get("RequestId"));
                    return MessageSendResult.ok(provider(), serial);
                }
                return MessageSendResult.fail(provider(), "TENCENT_SMS_" + orUnknown(code), detail);
            }
        }
        if (!resp.is2xx()) {
            return failFromHttp(resp);
        }
        return MessageSendResult.fail(provider(), "TENCENT_SMS_BAD_RESPONSE",
                "响应不是预期 JSON: " + abbreviate(resp.getBody()));
    }

    /** 裸号码补成 E.164（默认 +86），已经带 + 的原样用；形状不对就地拒绝。 */
    private static String e164(String receiver) {
        String raw = trimToNull(receiver);
        if (raw == null) {
            throw new IllegalArgumentException("手机号为空");
        }
        String phone = raw.startsWith("+") ? raw : "+86" + raw.replace("-", "").replace(" ", "");
        if (!phone.matches(E164)) {
            throw new IllegalArgumentException("号码不是 E.164 形状（腾讯云要求 +国家码+号码）");
        }
        return phone;
    }

    /**
     * TemplateParamSet：显式 {@code param("templateParamSet")}（逗号分隔，顺序即模板顺序）优先；
     * 否则把 params 去掉保留字后按 key 字典序取值。
     */
    private static List<String> templateParamSet(Message message) {
        String explicit = trimToNull(message.param("templateParamSet", null));
        List<String> out = new ArrayList<>();
        if (explicit != null) {
            for (String v : explicit.split(",")) {
                out.add(v);
            }
            return out;
        }
        Map<String, String> sorted = new TreeMap<>(message.getParams());
        sorted.remove("signName");
        sorted.remove("templateCode");
        sorted.remove("templateParamSet");
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            out.add(e.getValue());
        }
        return out;
    }

    /** URL 的 host[:port] —— 与 HttpURLConnection 真正发出的 Host 头同源，别另写字面量。 */
    static String hostOf(String url) {
        try {
            URL u = new URL(url);
            int port = u.getPort();
            return u.getHost() + (port > 0 ? ":" + port : "");
        } catch (Exception e) {
            throw new IllegalArgumentException("URL 无法解析: " + SimpleHttpClient.safeTarget(url), e);
        }
    }

    /** canonical URI：URL 的 path 段，空 path 补成 "/"（TC3 要求这一段存在）。 */
    private static String canonicalUriOf(String url) {
        try {
            String path = new URL(url).getPath();
            return path == null || path.isEmpty() ? "/" : path;
        } catch (Exception e) {
            return "/";
        }
    }

    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            return m;
        }
        return null;
    }

    private static List<Object> asList(Object o) {
        if (o instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> l = (List<Object>) o;
            return l;
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 回包里缺 Code 时，错误码别拼成 "..._null"。 */
    private static String orUnknown(Object o) {
        String s = str(o);
        return s == null ? "Unknown" : s;
    }
}
