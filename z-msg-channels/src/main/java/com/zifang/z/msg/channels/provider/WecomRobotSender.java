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
 * 企业微信群机器人（群自定义机器人 webhook）。
 * <p>
 * 官方形状：{@code POST https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=<key>}，
 * body {@code {"msgtype":"text","text":{"content":"...","mentioned_mobile_list":[...]}}}。
 * 无签名机制（key 即凭据），响应 {@code {"errcode":0,"errmsg":"ok"}}，
 * <b>errcode != 0 ⇒ 业务拒绝</b>，返回 fail 并把 errmsg 原样带上（任务书点名的行为）。
 * <p>
 * 不支持（待核对）：markdown / image / news / file 等 msgtype、应用消息
 * （access_token + agentid 那条链路属 IM_WEIXIN_MP 的扩展位，未实现）。
 */
public class WecomRobotSender extends AbstractHttpChannelSender {

    public static final String PROVIDER = "robot";
    private static final String DEFAULT_BASE = "https://qyapi.weixin.qq.com";

    public WecomRobotSender(ChannelsProperties properties, SimpleHttpClient http) {
        super(properties, http, Channels.IM_WECOM, PROVIDER);
    }

    @Override
    protected boolean configured() {
        ChannelsProperties.ChannelCfg cfg = cfg();
        return trimToNull(cfg.getToken()) != null || trimToNull(cfg.getUrl()) != null;
    }

    @Override
    protected MessageSendResult doSend(Message message) {
        String urlOverride = trimToNull(cfg().getUrl());
        String key = trimToNull(cfg().getToken());
        if (urlOverride == null && key == null) {
            return MessageSendResult.fail(provider(), "PROVIDER_NOT_CONFIGURED",
                    "z-msg.channel.im-wecom.token(key) 未配置");
        }
        String text = robotText(message);
        if (text == null) {
            return MessageSendResult.fail(provider(), "INVALID_CONTENT", "subject/content 均为空");
        }
        String url = urlOverride != null ? urlOverride
                : buildUrl(null, DEFAULT_BASE, "/cgi-bin/webhook/send")
                + "?key=" + Signatures.percentEncode(key);

        Map<String, Object> textNode = new LinkedHashMap<>();
        textNode.put("content", text);
        List<String> mentioned = mentionedMobiles(message);
        if (!mentioned.isEmpty()) {
            textNode.put("mentioned_mobile_list", mentioned);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "text");
        body.put("text", textNode);

        HttpResponse resp = postJson(url, MsgJson.toJson(body), null);
        if (!resp.is2xx()) {
            return failFromHttp(resp);
        }
        Map<String, Object> json = MsgJson.toMap(resp.getBody());
        if (json == null) {
            return MessageSendResult.fail(provider(), "WECOM_BAD_RESPONSE",
                    "响应不是 JSON: " + abbreviate(resp.getBody()));
        }
        long errcode = num(json.get("errcode"));
        if (errcode != 0) {
            return MessageSendResult.fail(provider(), "WECOM_" + errcode,
                    String.valueOf(json.get("errmsg")));
        }
        log.info("[z-msg-wecom] OK msgId={} target={}", message.getMsgId(),
                SimpleHttpClient.safeTarget(url));
        return MessageSendResult.ok(provider(), "WECOM-" + message.getMsgId());
    }

    @SuppressWarnings("unchecked")
    private List<String> mentionedMobiles(Message message) {
        Object v = message.getVendorOptions().get("mentionedMobiles");
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
}
