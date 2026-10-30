package com.zifang.z.msg.web.api;

import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageGateway;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.web.config.MsgWebProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 直发端点：{@code POST /api/msg/send} —— 指定渠道 + 指定收件人 + 运行时全文，一行透传。
 * <p>
 * <b>为什么要有它</b>：{@code POST /api/msg/publish} 收的是「业务事件」，正文由模板渲染出来；
 * 跨进程调用方要发的是「这一次的全文」（渲染参数由它自己算好了），没有模板可用。
 * 这个端点就是那一条缺口，落地路径是 {@link MessageGateway#send(Message)} —— 走的是
 * <b>和业务侧一模一样的管道</b>：偏好过滤 → 静默时段 → 限流 → 模板兜底 → 幂等占位 →
 * 发送 → 重试 → 降级 → 投递日志（审计）。它<b>不</b>碰 {@code MessageGateway} / {@code ChannelRouter} 内部。
 * <p>
 * <b>门禁姿势与 publish 同款</b>：默认关，宿主用 {@code z-msg.web.send-endpoint-enabled=true}
 * 显式打开；打开之前请先确认调用方在网络边界内（它能往<b>任意</b>收件人发消息，
 * 和 publish 一样，是个未认证的投递入口）。
 * <p>
 * <b>关着的时候回什么</b>：<b>真 HTTP 403</b> + {@code Result} 正文（业务码 403）。
 * 这一条与 publish 的历史口径（HTTP 200 + 业务 code:403）<b>不一样</b>，是有意为之：
 * publish 那个 200 早被 README 记成「关着时实测 HTTP 200 + 业务 code:403，不是 HTTP 403」，
 * 属于历史包袱；而这个端点是<b>新加</b>的，没有历史包袱可背，就按仓库里管理面闸门
 * （{@code AdminEndpointGate}）已经立好的口径来 —— 上游网关和监控看得到真状态码。
 * publish 自己那套口径<b>保持不变</b>（回归钉在 {@code MsgPublishGateDisabledTest} /
 * {@code MsgPublishGateEnabledTest}），不因为多了这个端点就顺手"统一"掉。
 * <p>
 * <b>校验</b>（三条，都是 4xx + 人话）：
 * <ol>
 *   <li>channel 必须是已知通道；</li>
 *   <li>{@code receiver} 与 {@code userId} 至少给一个（站内信走 userId，短信/邮箱走 receiver）；</li>
 *   <li>{@code content} 非空。</li>
 * </ol>
 * 校验放在<b>门禁之后</b>：闸门关着的时候，任何 body 都只回 403 —— 端点关着的时候，
 * 连"你的 body 合不合法"都不该告诉外人。
 */
@RestController
@RequestMapping("/api/msg")
public class MsgSendController {

    /**
     * 403 正文里的这句话同时是给宿主看的开法说明。
     */
    public static final String DISABLED_MESSAGE =
            "send 端点未启用：确认调用方已在认证边界内后设置 z-msg.web.send-endpoint-enabled=true";

    /**
     * {@code bizType} 为空时的归档值。
     * <p>
     * 为什么给默认值而不是报错：站内信落库那一列（{@code event_type}）是 NOT NULL，
     * bizType 空着会一路走到 INSERT 才炸成一条看不懂的 502；而 bizType 只是归档口径，
     * 不是"能不能发"的判断依据 —— 调用方没要求分类时，服务端替它归到 {@code DIRECT_SEND}
     * （这个值会明明白白出现在收件箱与投递日志里，不是黑箱）。
     */
    public static final String DEFAULT_BIZ_TYPE = "DIRECT_SEND";

    @Resource
    private MessageGateway messageGateway;
    @Resource
    private MsgWebProperties webProperties;

    /**
     * 直发一条消息。
     *
     * @param req 请求体（{@code Message} 的直填子集），可空
     * @return 成功时 {@code data} 里带 {@code msgId}；失败时是带人话的 4xx/5xx
     */
    @PostMapping("/send")
    public ResponseEntity<Result<Map<String, Object>>> send(@RequestBody(required = false) MsgSendRequest req) {
        // 1. 门禁先行：端点关着的时候，body 什么样都不看
        if (!webProperties.isSendEndpointEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.<Map<String, Object>>fail(DISABLED_MESSAGE).code(403));
        }

        // 2. 校验（非法入参统一走 MsgWebExceptionHandler ⇒ HTTP 400 + 人话）
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

        // 3. 一行透传：请求体直填成 Message，其余交给 MessageGateway 既有管道
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

        // 4. 投递结果 → HTTP 状态码。网关返回的是结构化结果，不是异常，所以这里自己翻译：
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
