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

/**
 * 飞书群机器人（自定义机器人 webhook）。
 * <p>
 * 官方形状：{@code POST https://open.feishu.cn/open-apis/bot/v2/hook/<hook-token>}，
 * body {@code {"msg_type":"text","content":{"text":"..."}}}。
 * 开启"签名校验"时 body 追加 {@code "timestamp":"<秒>", "sign":"Base64(HmacSHA256(key=timestamp+"\n"+secret, data=""))"}。
 * <p>
 * 注意与钉钉的差异（推翻任务书假设的一处）：飞书的 HMAC 密钥是 "timestamp 换行 secret" 整串、
 * 被签数据是<b>空串</b>，而不是"密钥=secret 本身"；timestamp 单位是秒不是毫秒。
 * 若与实际官方文档不符请以文档为准并修正 {@link Signatures#feishuSign}。
 * <p>
 * 成功响应形如 {@code {"code":0,"msg":"success","data":{...}}}，code!=0 视为业务拒绝。
 * 不支持（待核对）：富文本 post/interactive 卡片、@人语法（at 用户需要 user_id 查询接口）。
 */
public class FeishuRobotSender extends AbstractHttpChannelSender {

    public static final String PROVIDER = "robot";
    private static final String DEFAULT_BASE = "https://open.feishu.cn";

    public FeishuRobotSender(ChannelsProperties properties, SimpleHttpClient http) {
        super(properties, http, Channels.IM_FEISHU, PROVIDER);
    }

    @Override
    protected boolean configured() {
        ChannelsProperties.ChannelCfg cfg = cfg();
        return trimToNull(cfg.getUrl()) != null || trimToNull(cfg.getToken()) != null;
    }

    @Override
    protected MessageSendResult doSend(Message message) {
        String token = trimToNull(cfg().getToken());
        if (trimToNull(cfg().getUrl()) == null && token == null) {
            return MessageSendResult.fail(provider(), "PROVIDER_NOT_CONFIGURED",
                    "z-msg.channel.im-feishu.url 与 token 至少配一个");
        }
        String text = robotText(message);
        if (text == null) {
            return MessageSendResult.fail(provider(), "INVALID_CONTENT", "subject/content 均为空");
        }

        String url = trimToNull(cfg().getUrl()) != null
                ? cfg().getUrl().trim()
                : buildUrl(null, DEFAULT_BASE, "/open-apis/bot/v2/hook/" + Signatures.percentEncode(token));

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("text", text);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "text");
        body.put("content", content);

        String secret = trimToNull(cfg().getSecret());
        if (secret != null) {
            long ts = currentTimeMillis() / 1000L;
            body.put("timestamp", String.valueOf(ts));
            body.put("sign", Signatures.feishuSign(secret, ts));
        }

        HttpResponse resp = postJson(url, MsgJson.toJson(body), null);
        if (!resp.is2xx()) {
            return failFromHttp(resp);
        }
        Map<String, Object> json = MsgJson.toMap(resp.getBody());
        if (json == null) {
            return MessageSendResult.fail(provider(), "FEISHU_BAD_RESPONSE",
                    "响应不是 JSON: " + abbreviate(resp.getBody()));
        }
        long code = num(json.get("code"));
        if (code != 0) {
            return MessageSendResult.fail(provider(), "FEISHU_" + code, String.valueOf(json.get("msg")));
        }
        log.info("[z-msg-feishu] OK msgId={} target={}", message.getMsgId(),
                SimpleHttpClient.safeTarget(url));
        return MessageSendResult.ok(provider(), "FEISHU-" + message.getMsgId());
    }

    private static long num(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : Long.MIN_VALUE;
    }

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
