package com.zifang.z.msg.channels.provider;

import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.channels.config.ChannelsProperties;
import com.zifang.z.msg.channels.http.HttpResponse;
import com.zifang.z.msg.channels.http.SimpleHttpClient;
import com.zifang.z.msg.channels.util.Signatures;
import com.zifang.z.msg.core.json.MsgJson;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 阿里云短信（dysmsapi，RPC/POP 风格 OpenAPI，HMAC-SHA1 排序签名）。
 * <p>
 * 官方形状（RPC 风格）：公共参数 {@code Action=SendSms, Version=2017-05-25, Format=JSON,
 * AccessKeyId, SignatureMethod=HMAC-SHA1, SignatureVersion=1.0, SignatureNonce,
 * Timestamp(UTC ISO8601)} + 业务参数 {@code PhoneNumbers, SignName, TemplateCode, TemplateParam}；
 * 全部参数按 key 字典序拼 canonical query，
 * {@code stringToSign = POST&%2F&percentEncode(canonical)}，
 * {@code Signature = Base64(HmacSHA1(accessKeySecret + "&", stringToSign))}，
 * 随其余参数一起 POST 在 query string 上。
 * 响应 {@code {"Code":"OK","RequestId":"...","BizId":"..."}}；Code != OK 即业务拒绝
 * （isv.SMS_SIGNATURE_ILLEGAL / isv.TEMPLATE_MISSING_PARAMETERS / 频控等），翻成 fail。
 * <p>
 * nonce / timestamp 通过构造参数注入：生产给时钟与 UUID，测试给固定值，
 * 从而能钉"固定输入 → 固定签名"的向量（期望值由独立参考实现离线算好，不是本类自产）。
 * <p>
 * 待与官方文档核对：BatchSendSms 批量接口、PhoneNumbers 多号码逗号分隔语义、
 * TemplateParam 中 JSON key 与模板 ${var} 的严格对应（本类把 message.params 去掉
 * signName/templateCode 保留字后整体作为 TemplateParam 上送）。
 */
public class AliyunSmsSender extends AbstractHttpChannelSender {

    public static final String PROVIDER = "aliyun";
    private static final String DEFAULT_BASE = "https://dysmsapi.aliyuncs.com";
    private static final String API_VERSION = "2017-05-25";

    private final Supplier<String> nonceSupplier;
    private final Supplier<String> timestampSupplier;

    public AliyunSmsSender(ChannelsProperties properties, SimpleHttpClient http) {
        this(properties, http, AliyunSmsSender::randomNonce, AliyunSmsSender::utcTimestamp);
    }

    public AliyunSmsSender(ChannelsProperties properties, SimpleHttpClient http,
                           Supplier<String> nonceSupplier, Supplier<String> timestampSupplier) {
        super(properties, http, Channels.SMS, PROVIDER);
        this.nonceSupplier = nonceSupplier;
        this.timestampSupplier = timestampSupplier;
    }

    @Override
    protected boolean configured() {
        ChannelsProperties.ChannelCfg cfg = cfg();
        return trimToNull(cfg.getAccessKeyId()) != null
                && trimToNull(cfg.getAccessKeySecret()) != null;
    }

    @Override
    protected MessageSendResult doSend(Message message) {
        ChannelsProperties.ChannelCfg cfg = cfg();
        String accessKeyId = trimToNull(cfg.getAccessKeyId());
        String accessKeySecret = trimToNull(cfg.getAccessKeySecret());
        if (accessKeyId == null || accessKeySecret == null) {
            return MessageSendResult.fail(provider(), "PROVIDER_NOT_CONFIGURED",
                    "z-msg.channel.sms.access-key-id / access-key-secret 未配置");
        }
        String phone = trimToNull(message.getReceiver());
        if (phone == null) {
            return MessageSendResult.fail(provider(), "INVALID_RECEIVER", "手机号为空");
        }
        String signName = message.param("signName", null);
        if (trimToNull(signName) == null) {
            signName = trimToNull(cfg.getSignName());
        }
        if (signName == null) {
            return MessageSendResult.fail(provider(), "PROVIDER_NOT_CONFIGURED",
                    "短信签名 signName 未配置（param 或 z-msg.channel.sms.sign-name）");
        }
        String templateCode = message.param("templateCode", null);
        if (trimToNull(templateCode) == null) {
            templateCode = trimToNull(cfg.getTemplateCode());
        }
        if (templateCode == null) {
            return MessageSendResult.fail(provider(), "TEMPLATE_NOT_FOUND", "短信模板 code 为空");
        }
        String templateParam = templateParamJson(message);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("Action", "SendSms");
        params.put("Version", API_VERSION);
        params.put("Format", "JSON");
        params.put("AccessKeyId", accessKeyId);
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureVersion", trimToNull(cfg.getSignatureVersion()) == null
                ? "1.0" : cfg.getSignatureVersion().trim());
        params.put("SignatureNonce", nonceSupplier.get());
        params.put("Timestamp", timestampSupplier.get());
        params.put("RegionId", trimToNull(cfg.getRegion()) == null ? "cn-hangzhou" : cfg.getRegion().trim());
        params.put("PhoneNumbers", phone);
        params.put("SignName", signName);
        params.put("TemplateCode", templateCode);
        if (templateParam != null) {
            params.put("TemplateParam", templateParam);
        }

        Signatures.AliyunRpcSigned signed = Signatures.aliyunRpcSign("POST", params, accessKeySecret);

        StringBuilder url = new StringBuilder(buildUrl(cfg.getUrl(), DEFAULT_BASE, "/"))
                .append("?").append(signed.getCanonicalQuery())
                .append("&Signature=").append(Signatures.percentEncode(signed.getSignature()));

        HttpResponse resp = execute("POST", url.toString(), jsonHeaders(), "");
        if (!resp.is2xx()) {
            return failFromHttp(resp);
        }
        Map<String, Object> json = MsgJson.toMap(resp.getBody());
        if (json == null || json.get("Code") == null) {
            return MessageSendResult.fail(provider(), "ALIYUN_SMS_BAD_RESPONSE",
                    "响应不是预期 JSON: " + abbreviate(resp.getBody()));
        }
        String code = String.valueOf(json.get("Code"));
        if (!"OK".equalsIgnoreCase(code)) {
            return MessageSendResult.fail(provider(), "ALIYUN_SMS_" + code,
                    String.valueOf(json.get("Message")));
        }
        String bizId = json.get("BizId") == null ? String.valueOf(json.get("RequestId"))
                : String.valueOf(json.get("BizId"));
        log.info("[z-msg-aliyun-sms] OK msgId={} bizId={} requestId={}",
                message.getMsgId(), bizId, json.get("RequestId"));
        return MessageSendResult.ok(provider(), bizId);
    }

    private Map<String, String> jsonHeaders() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        return h;
    }

    /** params 去掉保留字后打包成 TemplateParam JSON；没有多余参数就不带该字段。 */
    private String templateParamJson(Message message) {
        Map<String, String> p = new LinkedHashMap<>(message.getParams());
        p.remove("signName");
        p.remove("templateCode");
        if (p.isEmpty()) {
            return null;
        }
        return MsgJson.toJson(p);
    }

    private static String randomNonce() {
        return UUID.randomUUID().toString();
    }

    private static String utcTimestamp() {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return fmt.format(new java.util.Date());
    }
}
