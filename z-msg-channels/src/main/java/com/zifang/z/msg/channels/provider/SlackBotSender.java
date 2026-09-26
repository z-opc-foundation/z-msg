package com.zifang.z.msg.channels.provider;

import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.channels.config.ChannelsProperties;
import com.zifang.z.msg.channels.http.HttpResponse;
import com.zifang.z.msg.channels.http.SimpleHttpClient;
import com.zifang.z.msg.core.json.MsgJson;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Slack 群消息（{@code chat.postMessage} Web API，bot user token）。
 * <p>
 * 官方形状：{@code POST https://slack.com/api/chat.postMessage}，
 * header {@code Authorization: Bearer xoxb-...}、{@code Content-Type: application/json}，
 * body {@code {"channel":"#chan|C123","text":"..."}}。
 * 响应 {@code {"ok":true,"channel":"...","ts":"..."}}；<b>ok:false ⇒ fail</b>，
 * 错误码在平铺的 {@code error} 字段（ratelimited / not_authed / channel_not_found...）。
 * <p>
 * channel 取值优先级：{@code message.receiver} > {@code z-msg.channel.im-slack.slack-channel}。
 * 不支持（待核对）：Blocks 富布局、线程回复（thread_ts）、unfurls 控制、
 * per-message rate limit（官方按 scope 分桶，未做客户端限速）。
 */
public class SlackBotSender extends AbstractHttpChannelSender {

    public static final String PROVIDER = "bot";
    private static final String DEFAULT_BASE = "https://slack.com";

    public SlackBotSender(ChannelsProperties properties, SimpleHttpClient http) {
        super(properties, http, Channels.IM_SLACK, PROVIDER);
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
                    "z-msg.channel.im-slack.token (xoxb-...) 未配置");
        }
        String target = trimToNull(message.getReceiver());
        if (target == null) {
            target = trimToNull(cfg().getSlackChannel());
        }
        if (target == null) {
            return MessageSendResult.fail(provider(), "INVALID_RECEIVER",
                    "receiver 与 z-msg.channel.im-slack.slack-channel 都为空");
        }
        String text = robotText(message);
        if (text == null) {
            return MessageSendResult.fail(provider(), "INVALID_CONTENT", "subject/content 均为空");
        }

        String url = buildUrl(cfg().getUrl(), DEFAULT_BASE, "/api/chat.postMessage");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", target);
        body.put("text", text);

        Map<String, String> headers = new LinkedHashMap<>();
        // 凭据只进 header；日志永远不打印 header 内容
        headers.put("Authorization", "Bearer " + token);

        HttpResponse resp = postJson(url, MsgJson.toJson(body), headers);
        if (!resp.is2xx()) {
            return failFromHttp(resp);
        }
        Map<String, Object> json = MsgJson.toMap(resp.getBody());
        if (json == null) {
            return MessageSendResult.fail(provider(), "SLACK_BAD_RESPONSE",
                    "响应不是 JSON: " + abbreviate(resp.getBody()));
        }
        if (!Boolean.TRUE.equals(json.get("ok"))) {
            Object err = json.get("error");
            return MessageSendResult.fail(provider(), "SLACK_" + (err == null ? "unknown" : err),
                    String.valueOf(err == null ? abbreviate(resp.getBody()) : err));
        }
        String ts = json.get("ts") == null ? null : String.valueOf(json.get("ts"));
        log.info("[z-msg-slack] OK msgId={} channel={} ts={}", message.getMsgId(), target, ts);
        return MessageSendResult.ok(provider(), ts == null ? "SLACK-" + message.getMsgId() : ts);
    }
}
