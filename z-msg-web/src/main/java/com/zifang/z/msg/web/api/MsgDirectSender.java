package com.zifang.z.msg.web.api;

import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageGateway;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.core.domain.entity.MsgClientDO;
import com.zifang.z.msg.core.domain.service.MsgClientService;
import com.zifang.z.msg.web.auth.MsgClientAuthInterceptor;
import com.zifang.z.msg.web.auth.MsgPrincipalResolver;
import com.zifang.z.msg.web.config.MsgWebProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 直发管道：把「准入」与「投递」分开，两个入口共用一条投递管道。
 * <p>
 * 两个入口：
 * <ul>
 *   <li>{@code POST /api/msg/send} —— 平台口径：AK 客户端 / 平台用户身份 / 老开关（向后兼容）；</li>
 *   <li>{@code POST /api/msg/open/send} —— 对外口径（机器对机器）：<b>只认 AK</b>。</li>
 * </ul>
 * 两个入口的差别<b>只在"凭什么认定你是你"</b>，投递路径完全一致
 * （偏好过滤 → 静默时段 → 限流 → 模板兜底 → 幂等占位 → 发送 → 重试 → 降级 → 投递日志）。
 * <p>
 * 拆成两半的原因：对外端点要豁免宿主 SSO 闸门（机器没有用户 cookie），
 * 一旦豁免就没有任何登录态可依 ⇒ 那个端点只能 fail-closed 地只认 AK。
 * 平台端点仍走宿主闸门 ⇒ 页面上的人发消息不受影响，两条路互不牵连。
 */
@Component
public class MsgDirectSender {

    /**
     * 401 正文：没带任何可识别身份时回这句。
     */
    public static final String UNAUTHORIZED_MESSAGE =
            "未识别调用者身份：带 X-Api-Key（客户端 AK）或平台登录态才能发消息";

    /**
     * {@code bizType} 为空时的归档值（站内信落库那一列是 NOT NULL，空着会一路走到 INSERT 才炸）。
     */
    public static final String DEFAULT_BIZ_TYPE = "DIRECT_SEND";

    private final MessageGateway messageGateway;
    private final MsgWebProperties webProperties;
    private final MsgPrincipalResolver principalResolver;
    private final MsgClientService clientService;

    public MsgDirectSender(MessageGateway messageGateway,
                           MsgWebProperties webProperties,
                           MsgPrincipalResolver principalResolver,
                           MsgClientService clientService) {
        this.messageGateway = messageGateway;
        this.webProperties = webProperties;
        this.principalResolver = principalResolver;
        this.clientService = clientService;
    }

    /**
     * 平台口径的准入 + 投递。
     */
    public ResponseEntity<Result<Map<String, Object>>> sendForPlatform(MsgSendRequest req,
                                                                       HttpServletRequest request) {
        MsgClientDO client = MsgClientAuthInterceptor.clientOf(request);
        if (client != null) {
            // 带 AK 的请求按 AK 口径走（受通道白名单约束）
            return dispatch(req, client);
        }
        Long principalUserId = principalResolver.resolve(request);
        if (principalUserId == null && !webProperties.isSendEndpointEnabled()) {
            return unauthorized(UNAUTHORIZED_MESSAGE);
        }
        return dispatch(req, null);
    }

    /**
     * 对外口径的准入 + 投递：<b>AK 必填</b>（没有 AK ⇒ 401，不退回到平台身份）。
     * <p>
     * 为什么不容许平台身份：这个端点豁免了宿主 SSO 闸门，闸门那份"你是谁"的判定
     * 已经不在了；再让它接受匿名调用，等于开了一个谁都能用的投递口。
     */
    public ResponseEntity<Result<Map<String, Object>>> sendForClient(MsgSendRequest req, MsgClientDO client) {
        if (client == null) {
            return unauthorized(MsgOpenSendController.NO_API_KEY_MESSAGE);
        }
        return dispatch(req, client);
    }

    /**
     * 投递管道本体：校验 → 透传成 {@link Message} → 走 {@link MessageGateway} → 翻译结果。
     */
    private ResponseEntity<Result<Map<String, Object>>> dispatch(MsgSendRequest req, MsgClientDO client) {
        // 1. 校验（非法入参统一走 MsgWebExceptionHandler ⇒ HTTP 400 + 人话）
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空：至少要有 channel、receiver 或 userId、content");
        }
        String channel = Channels.normalize(req.getChannel());
        if (channel == null || !Channels.isKnown(channel)) {
            throw new IllegalArgumentException("未知通道：" + req.getChannel()
                    + "（可用通道见 GET /api/msg/channel/list）");
        }
        if (isBlank(req.getReceiver()) && req.getUserId() == null) {
            throw new IllegalArgumentException("receiver 与 userId 至少要提供一个："
                    + "站内信走 userId，短信/邮箱走 receiver");
        }
        if (isBlank(req.getContent())) {
            throw new IllegalArgumentException("content 不能为空");
        }
        // AK 客户端的通道白名单（页面那种平台用户身份没有这个约束）
        if (client != null && !clientService.verifyChannel(client, channel)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.<Map<String, Object>>fail(
                            "客户端 " + client.getAppCode() + " 无权使用通道 " + channel).code(403));
        }

        // 2. 一行透传：请求体直填成 Message，其余交给 MessageGateway 既有管道
        Message message = Message.builder()
                .channel(channel)
                .receiver(trimToNull(req.getReceiver()))
                .userId(req.getUserId())
                .subject(trimToNull(req.getSubject()))
                .content(req.getContent())
                .linkUrl(trimToNull(req.getLinkUrl()))
                .bizType(isBlank(req.getBizType()) ? DEFAULT_BIZ_TYPE : req.getBizType().trim())
                .idempotencyKey(trimToNull(req.getIdempotencyKey()))
                .build();

        MessageSendResult result = messageGateway.send(message);

        // 3. 投递结果 → HTTP 状态码。网关返回的是结构化结果，不是异常，所以这里自己翻译：
        //    调用方看到的是"这次为什么没发出去"，而不是一个笼统的 500。
        if (!result.isSuccess()) {
            return failure(message, result);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("msgId", message.getMsgId());
        data.put("channel", message.getChannel());
        data.put("provider", result.getProvider());
        data.put("bizType", message.getBizType());
        return ResponseEntity.ok(Result.success(data));
    }

    /**
     * 投递失败 → 状态码 + 人话。
     * <p>
     * 分档的依据是「谁该改」：入参问题回 400（调用方改 body）、配额问题回 429（调用方降速）、
     * 策略挡下（偏好 / 静默 / 幂等命中）回 409（重试没用）、厂商侧问题回 502/503（等服务方或换厂商）。
     */
    private ResponseEntity<Result<Map<String, Object>>> failure(Message message, MessageSendResult result) {
        String code = result.getErrorCode();
        String detail = result.getErrorMessage() == null ? "" : result.getErrorMessage();
        HttpStatus status;
        if ("INVALID_RECEIVER".equals(code) || "UNSUPPORTED_CHANNEL".equals(code)) {
            status = HttpStatus.BAD_REQUEST;
        } else if ("RATE_LIMIT".equals(code) || "USER_RATE_LIMIT".equals(code)) {
            status = HttpStatus.TOO_MANY_REQUESTS;
        } else if ("PREF_BLOCKED".equals(code) || "QUIET_HOURS".equals(code)
                || "DUPLICATED".equals(code)) {
            status = HttpStatus.CONFLICT;
        } else if ("PROVIDER_NOT_CONFIGURED".equals(code)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
        } else {
            status = HttpStatus.BAD_GATEWAY;
        }
        String text = "发送失败[" + code + "]：" + detail
                + "（channel=" + message.getChannel() + ", provider=" + result.getProvider() + "）";
        return ResponseEntity.status(status)
                .body(Result.<Map<String, Object>>fail(text).code(status.value()));
    }

    static ResponseEntity<Result<Map<String, Object>>> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.<Map<String, Object>>fail(message).code(401));
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
