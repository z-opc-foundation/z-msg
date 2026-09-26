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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉群机器人（自定义机器人 webhook）。
 * <p>
 * 官方形状：{@code POST https://oapi.dingtalk.com/robot/send?access_token=...}；
 * 开启"加签"安全设置时需追加 {@code &timestamp=<ms>&sign=<urlencode(Base64(HmacSHA256(secret,
 * timestamp + "\n" + secret)))>}，secret 同时是 HMAC 密钥和被拼进消息体的串的一部分。
 * body 为 {@code {"msgtype":"text","text":{"content":"..."}}}。
 * <p>
 * 配置：{@code z-msg.channel.im-dingtalk.token} 必填；{@code secret} 可选（机器人开了加签才填）。
 * <p>
 * 明确不支持（留待与官方文档核对后再加）：markdown/link/actionCard/feedCard 消息、
 * at.atMobiles/atUserIds 之外的 @ 语法、机器人限流额度（20 条/分钟，超限时 errcode=130101，
 * 本类会如实翻成 fail，但不做客户端侧限速）。
 */
public class DingTalkRobotSender extends AbstractHttpChannelSender {

    public static final String PROVIDER = "robot";
    private static final String DEFAULT_BASE = "https://oapi.dingtalk.com";

    public DingTalkRobotSender(ChannelsProperties properties, SimpleHttpClient http) {
        super(properties, http, Channels.IM_DINGTALK, PROVIDER);
    }

    @Override
    protected boolean configured() {
        return trimToNull(cfg().getToken()) != null;
    }

    @Override
    protected MessageSendResult doSend(Message message) {
        String token = trimToNull(cfg().getToken());
        if (token == null) {
            return MessageSendResult.fail(provider(), "PROVIDER_NOT_CONFIGURED",
                    "z-msg.channel.im-dingtalk.token 未配置");
        }
        String text = robotText(message);
        if (text == null) {
            return MessageSendResult.fail(provider(), "INVALID_CONTENT", "subject/content 均为空");
        }

        String base = buildUrl(cfg().getUrl(), DEFAULT_BASE, "/robot/send");
        StringBuilder url = new StringBuilder(base)
                .append(base.indexOf('?') >= 0 ? "&" : "?")
                .append("access_token=").append(Signatures.percentEncode(token));
        String secret = trimToNull(cfg().getSecret());
        if (secret != null) {
            long ts = currentTimeMillis();
            url.append("&timestamp=").append(ts)
                    .append("&sign=").append(Signatures.dingTalkSign(secret, ts));
        }

        Map<String, Object> textNode = new LinkedHashMap<>();
        textNode.put("content", text);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "text");
        body.put("text", textNode);
        List<String> atMobiles = atMobiles(message);
        if (!atMobiles.isEmpty()) {
            Map<String, Object> at = new LinkedHashMap<>();
            at.put("atMobiles", atMobiles);
            at.put("isAtAll", Boolean.FALSE);
            body.put("at", at);
        }

        HttpResponse resp = postJson(url.toString(), MsgJson.toJson(body), null);
        if (!resp.is2xx()) {
            return failFromHttp(resp);
        }
        Map<String, Object> json = MsgJson.toMap(resp.getBody());
        if (json == null) {
            return MessageSendResult.fail(provider(), "DINGTALK_BAD_RESPONSE",
                    "响应不是 JSON: " + abbreviate(resp.getBody()));
        }
        long errcode = num(json.get("errcode"));
        if (errcode != 0) {
            return MessageSendResult.fail(provider(), "DINGTALK_" + errcode,
                    String.valueOf(json.get("errmsg")));
        }
        log.info("[z-msg-dingtalk] OK msgId={} receiver={} target={}",
                message.getMsgId(), message.getReceiver(),
                SimpleHttpClient.safeTarget(url.toString()));
        return MessageSendResult.ok(provider(), "DINGTALK-" + message.getMsgId());
    }

    /** vendorOptions.atMobiles（List 或逗号分隔串）。 */
    @SuppressWarnings("unchecked")
    private List<String> atMobiles(Message message) {
        Object v = message.getVendorOptions().get("atMobiles");
        List<String> out = new ArrayList<>();
        if (v instanceof List) {
            for (Object o : (List<Object>) v) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        } else if (v instanceof String && !((String) v).trim().isEmpty()) {
            for (String s : ((String) v).split(",")) {
                if (!s.trim().isEmpty()) {
                    out.add(s.trim());
                }
            }
        }
        return out;
    }

    private static long num(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : Long.MIN_VALUE;
    }

    /** 测试可覆写以钉死时间戳。 */
    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
