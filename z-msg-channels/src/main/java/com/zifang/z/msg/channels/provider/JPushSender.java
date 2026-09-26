package com.zifang.z.msg.channels.provider;

import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.channels.config.ChannelsProperties;
import com.zifang.z.msg.channels.http.HttpResponse;
import com.zifang.z.msg.channels.http.SimpleHttpClient;
import com.zifang.z.msg.channels.util.Signatures;
import com.zifang.z.msg.core.json.MsgJson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极光推送（JPush REST API v3，{@code POST /v3/push}）。
 * <p>
 * 官方形状：鉴权是 HTTP Basic，{@code Authorization: Basic Base64(AppKey + ":" + MasterSecret)}
 * ——没有签名、没有时间戳，凭据整段进 header；body
 * {@code {"platform":..., "audience":..., "notification":{"android":{"alert":...,"title":...},
 * "ios":{...}}}}；成功返回 {@code {"msg_id":"3596752105"}}，失败返回
 * {@code {"error":{"code":1004,"msg":"..."}}}（常见配着 4xx 状态码），错误码原样翻成
 * {@code JPUSH_<code>}。
 * <p>
 * audience 的选法（极光侧"发给谁"的三种正规写法，按优先级）：
 * {@code receiver="all"} → 广播；{@code param("alias")} / {@code param("tag")}（逗号分隔）
 * → 别名字定组；否则 {@code {"registration_id":[receiver]}} 单设备。
 * platform 缺省 "all"，可用 {@code param("platform")} 逗号分隔（如 {@code android,ios}）。
 * <p>
 * 不支持（待与官方文档核对）：{@code message}/{@code custom_msg}（在线透传）、
 * {@code sms}、{@code live_activity}、定向推送 push_all 的 task_id 异步接口、
 * 以及 /v3/receive、/v3/report 这类回执与统计查询。
 */
public class JPushSender extends AbstractHttpChannelSender {

    public static final String PROVIDER = "jpush";
    private static final String DEFAULT_BASE = "https://api.jpush.cn";
    private static final String PUSH_PATH = "/v3/push";

    public JPushSender(ChannelsProperties properties, SimpleHttpClient http) {
        super(properties, http, Channels.PUSH_JPUSH, PROVIDER);
    }

    @Override
    protected boolean configured() {
        ChannelsProperties.ChannelCfg cfg = cfg();
        return trimToNull(cfg.getAppKey()) != null && trimToNull(cfg.getMasterSecret()) != null;
    }

    @Override
    protected MessageSendResult doSend(Message message) {
        ChannelsProperties.ChannelCfg cfg = cfg();
        String appKey = trimToNull(cfg.getAppKey());
        String masterSecret = trimToNull(cfg.getMasterSecret());
        if (appKey == null || masterSecret == null) {
            return MessageSendResult.fail(provider(), "PROVIDER_NOT_CONFIGURED",
                    "z-msg.channel.push-jpush.app-key / master-secret 未配置");
        }
        Object audience = audience(message);
        if (audience == null) {
            return MessageSendResult.fail(provider(), "INVALID_RECEIVER",
                    "receiver 为空（registration_id / \"all\" / param(\"alias\") / param(\"tag\") 至少要一个）");
        }
        String alert = trimToNull(message.getContent());
        String title = trimToNull(message.getSubject());
        if (alert == null && title == null) {
            return MessageSendResult.fail(provider(), "INVALID_CONTENT", "推送正文为空");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("platform", platform(message));
        body.put("audience", audience);
        body.put("notification", notification(alert == null ? title : alert, title));

        String url = buildUrl(cfg.getUrl(), DEFAULT_BASE, PUSH_PATH);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", Signatures.basicAuth(appKey, masterSecret));
        HttpResponse resp = postJson(url, MsgJson.toJson(body), headers);

        Map<String, Object> json = MsgJson.toMap(resp.getBody());
        Map<String, Object> error = asMap(json == null ? null : json.get("error"));
        if (error != null) {
            Object code = error.get("code") == null ? "Unknown" : error.get("code");
            return MessageSendResult.fail(provider(), "JPUSH_" + code, String.valueOf(error.get("msg")));
        }
        if (!resp.is2xx()) {
            return failFromHttp(resp);
        }
        if (json == null || json.get("msg_id") == null) {
            return MessageSendResult.fail(provider(), "JPUSH_BAD_RESPONSE",
                    "响应不是预期 JSON: " + abbreviate(resp.getBody()));
        }
        String msgId = String.valueOf(json.get("msg_id"));
        log.info("[z-msg-jpush] OK msgId={} jpushMsgId={} target={}", message.getMsgId(), msgId,
                SimpleHttpClient.safeTarget(url));
        return MessageSendResult.ok(provider(), msgId);
    }

    /** "all" / alias / tag / registration_id 四选一；都没给返回 null。 */
    private static Object audience(Message message) {
        String alias = trimToNull(message.param("alias", null));
        String tag = trimToNull(message.param("tag", null));
        String receiver = trimToNull(message.getReceiver());
        if (alias != null) {
            return keyed("alias", split(alias));
        }
        if (tag != null) {
            return keyed("tag", split(tag));
        }
        if (receiver == null) {
            return null;
        }
        if ("all".equalsIgnoreCase(receiver)) {
            return "all";
        }
        return keyed("registration_id", split(receiver));
    }

    private static Map<String, Object> keyed(String key, List<String> values) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, values);
        return m;
    }

    private static Object platform(Message message) {
        String p = trimToNull(message.param("platform", null));
        return p == null ? "all" : split(p);
    }

    /** android/ios 两段的 alert+title 一致：极光按 platform 分发，两端字段形状相同。 */
    private static Map<String, Object> notification(String alert, String title) {
        Map<String, Object> perPlatform = new LinkedHashMap<>();
        perPlatform.put("alert", alert);
        if (title != null) {
            perPlatform.put("title", title);
        }
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("android", new LinkedHashMap<>(perPlatform));
        notification.put("ios", new LinkedHashMap<>(perPlatform));
        return notification;
    }

    private static List<String> split(String csv) {
        List<String> out = new ArrayList<>();
        for (String s : csv.split(",")) {
            String v = trimToNull(s);
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            return m;
        }
        return null;
    }
}
