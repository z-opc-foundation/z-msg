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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信公众号模板消息（{@code cgi-bin/message/template/send}）。
 * <p>
 * 官方形状：
 * <ol>
 *   <li>{@code GET /cgi-bin/token?grant_type=client_credential&appid=&secret=} →
 *       {@code {"access_token":"...","expires_in":7200}}</li>
 *   <li>{@code POST /cgi-bin/message/template/send?access_token=X}，body
 *       {@code {"touser":"<openid>","template_id":"...","url":"...","data":{"key":{"value":"v"}}}}</li>
 * </ol>
 * <p>
 * access_token 按 appId 缓存在过期前 5 分钟复用（微信官方限流：每日取 token 次数有限，
 * 且 token 全局唯一——每条消息都取一次既慢又可能把上一个顶掉）。
 * 发送返回 {@code errcode=40001/42001}（token 无效/过期）时作废缓存、重取一次并重发一次；
 * 其它 errcode 直接 fail 并带上 errmsg。
 * <p>
 * 不支持（待核对）：小程序订阅消息（IM_WEIXIN_MINI，另一条 send 接口）、
 * 模板消息 miniprogram 跳转字段、回调加解密（本项目 z-msg-im 的事，不在此模块）。
 */
public class WeixinMpSender extends AbstractHttpChannelSender {

    public static final String PROVIDER = "mp";
    private static final String DEFAULT_BASE = "https://api.weixin.qq.com";
    /** 过期前提前刷新，吸收时钟偏差与"取到即用即过期"边界 */
    private static final long TOKEN_REFRESH_MARGIN_MS = 5 * 60 * 1000L;
    private static final long DEFAULT_TOKEN_TTL_MS = 7200 * 1000L;
    /** token 失效错误码：40001 不正确 / 42001 过期 / 40014 不合法 */
    private static final int ERRCODE_INVALID_CREDENTIAL = 40001;
    private static final int ERRCODE_TOKEN_EXPIRED = 42001;
    private static final int ERRCODE_ILLEGAL_TOKEN = 40014;

    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

    public WeixinMpSender(ChannelsProperties properties, SimpleHttpClient http) {
        super(properties, http, Channels.IM_WEIXIN_MP, PROVIDER);
    }

    @Override
    protected boolean configured() {
        ChannelsProperties.ChannelCfg cfg = cfg();
        return trimToNull(cfg.getAppId()) != null && trimToNull(cfg.getAppSecret()) != null;
    }

    /** 测试用：清空 token 缓存 */
    public void invalidateAllTokens() {
        tokens.clear();
    }

    @Override
    protected MessageSendResult doSend(Message message) {
        ChannelsProperties.ChannelCfg cfg = cfg();
        String appId = trimToNull(cfg.getAppId());
        String appSecret = trimToNull(cfg.getAppSecret());
        if (appId == null || appSecret == null) {
            return MessageSendResult.fail(provider(), "PROVIDER_NOT_CONFIGURED",
                    "z-msg.channel.im-weixin-mp.app-id / app-secret 未配置");
        }
        String openid = trimToNull(message.getReceiver());
        if (openid == null) {
            return MessageSendResult.fail(provider(), "INVALID_RECEIVER", "receiver(openid) 为空");
        }
        String templateId = message.param("templateCode", null);
        if (trimToNull(templateId) == null) {
            templateId = trimToNull(cfg.getTemplateCode());
        }
        if (templateId == null) {
            return MessageSendResult.fail(provider(), "TEMPLATE_NOT_FOUND",
                    "template_id 为空（param(\"templateCode\") 与配置 template-code 都没给）");
        }

        String token = obtainToken(appId, appSecret, false);
        if (token == null) {
            return MessageSendResult.fail(provider(), "WEIXIN_TOKEN_FETCH_FAILED",
                    "access_token 获取失败（详见服务端日志，凭据不落日志）");
        }
        MessageSendResult result = postTemplate(token, openid, templateId, message);
        if (isTokenInvalid(result)) {
            // 作废缓存重取一次、重发一次——服务端侧各计一次请求，测试按 stub 计数钉这个行为
            log.warn("[z-msg-weixin-mp] token 失效(errcode={})，作废缓存重试 appId={}",
                    tokenErrcode(result), appId);
            invalidate(appId);
            token = obtainToken(appId, appSecret, true);
            if (token == null) {
                return MessageSendResult.fail(provider(), "WEIXIN_TOKEN_FETCH_FAILED",
                        "重试取 token 仍失败");
            }
            result = postTemplate(token, openid, templateId, message);
        }
        return result;
    }

    /** 结果里携带的原始 errcode 存这里，供上层判 40001/42001（fail 的 errorCode 形如 WEIXIN_40001） */
    private boolean isTokenInvalid(MessageSendResult r) {
        if (r == null || r.isSuccess()) {
            return false;
        }
        String code = r.getErrorCode();
        return code != null
                && (code.equals("WEIXIN_" + ERRCODE_INVALID_CREDENTIAL)
                || code.equals("WEIXIN_" + ERRCODE_TOKEN_EXPIRED)
                || code.equals("WEIXIN_" + ERRCODE_ILLEGAL_TOKEN));
    }

    private String tokenErrcode(MessageSendResult r) {
        return r == null ? "?" : r.getErrorCode();
    }

    private MessageSendResult postTemplate(String accessToken, String openid, String templateId,
                                           Message message) {
        String url = buildUrl(null, DEFAULT_BASE, "/cgi-bin/message/template/send")
                + "?access_token=" + Signatures.percentEncode(accessToken);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", openid);
        body.put("template_id", templateId);
        if (trimToNull(message.getLinkUrl()) != null) {
            body.put("url", message.getLinkUrl().trim());
        }
        // data：模板参数逐个包成 {"value": "..."}；key 与公众号后台模板字段一一对应
        Map<String, Object> data = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : message.getParams().entrySet()) {
            if ("templateCode".equals(e.getKey()) || "signName".equals(e.getKey())) {
                continue;
            }
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("value", e.getValue());
            data.put(e.getKey(), cell);
        }
        body.put("data", data);

        HttpResponse resp = postJson(url, MsgJson.toJson(body), null);
        if (!resp.is2xx()) {
            return failFromHttp(resp);
        }
        Map<String, Object> json = MsgJson.toMap(resp.getBody());
        if (json == null) {
            return MessageSendResult.fail(provider(), "WEIXIN_BAD_RESPONSE",
                    "响应不是 JSON: " + abbreviate(resp.getBody()));
        }
        long errcode = json.get("errcode") == null ? 0 : num(json.get("errcode"));
        if (errcode != 0) {
            return MessageSendResult.fail(provider(), "WEIXIN_" + errcode,
                    String.valueOf(json.get("errmsg")));
        }
        String msgId = json.get("msgid") == null ? "WEIXIN-" + message.getMsgId()
                : String.valueOf(json.get("msgid"));
        log.info("[z-msg-weixin-mp] OK msgId={} target={}", message.getMsgId(),
                SimpleHttpClient.safeTarget(url));
        return MessageSendResult.ok(provider(), msgId);
    }

    /** 取（或复用）access_token；失败返回 null。 */
    private String obtainToken(String appId, String appSecret, boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh) {
            CachedToken cached = tokens.get(appId);
            if (cached != null && cached.expireAtMs > now) {
                return cached.token;
            }
        }
        synchronized (("weixin-token:" + appId).intern()) {
            long nowInner = System.currentTimeMillis();
            CachedToken cached = tokens.get(appId);
            if (!forceRefresh && cached != null && cached.expireAtMs > nowInner) {
                return cached.token;
            }
            String url = buildUrl(null, DEFAULT_BASE, "/cgi-bin/token")
                    + "?grant_type=client_credential"
                    + "&appid=" + Signatures.percentEncode(appId)
                    + "&secret=" + Signatures.percentEncode(appSecret);
            HttpResponse resp = get(url, null);
            if (!resp.is2xx()) {
                log.warn("[z-msg-weixin-mp] 取 token 失败 http={} target={}",
                        resp.getStatus(), SimpleHttpClient.safeTarget(url));
                return null;
            }
            Map<String, Object> json = MsgJson.toMap(resp.getBody());
            if (json == null || json.get("access_token") == null) {
                log.warn("[z-msg-weixin-mp] 取 token 响应异常 errcode={}（errmsg 可能含敏感信息，不落全量）",
                        json == null ? "?" : json.get("errcode"));
                return null;
            }
            String token = String.valueOf(json.get("access_token"));
            long ttlMs = json.get("expires_in") == null
                    ? DEFAULT_TOKEN_TTL_MS : num(json.get("expires_in")) * 1000L;
            if (ttlMs <= TOKEN_REFRESH_MARGIN_MS) {
                ttlMs = DEFAULT_TOKEN_TTL_MS;
            }
            tokens.put(appId, new CachedToken(token, System.currentTimeMillis() + ttlMs - TOKEN_REFRESH_MARGIN_MS));
            return token;
        }
    }

    private void invalidate(String appId) {
        tokens.remove(appId);
    }

    private static long num(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }

    private static final class CachedToken {
        final String token;
        final long expireAtMs;

        CachedToken(String token, long expireAtMs) {
            this.token = token;
            this.expireAtMs = expireAtMs;
        }
    }
}
