package com.zifang.z.msg.web.api;

import com.zifang.util.core.meta.Result;
import com.zifang.z.meta.api.annotation.BizApi;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizParam;
import com.zifang.z.msg.api.MessageGateway;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 直发端点：{@code POST /api/msg/send} —— 指定渠道 + 指定收件人 + 运行时全文，一行透传。
 * <p>
 * <b>为什么要有它</b>：{@code POST /api/msg/publish} 收的是「业务事件」，正文由模板渲染出来；
 * 跨进程调用方要发的是「这一次的全文」（渲染参数由它自己算好了），没有模板可用。
 * 这个端点就是那一条缺口，落地路径是 {@link MessageGateway#send(com.zifang.z.msg.api.Message)} —— 走的是
 * <b>和业务侧一模一样的管道</b>：偏好过滤 → 静默时段 → 限流 → 模板兜底 → 幂等占位 →
 * 发送 → 重试 → 降级 → 投递日志（审计）。它<b>不</b>碰 {@code MessageGateway} / {@code ChannelRouter} 内部。
 * <p>
 * <b>门禁姿势</b>：准入口径是「身份」——AK 客户端（{@code X-Api-Key}）或平台用户身份
 * （宿主注入的 {@code MsgPrincipalResolver}）都能发；两者都没有才 401。
 * 老开关 {@code z-msg.web.send-endpoint-enabled=true} 仍然认，它会把裸调用也放行（向后兼容），
 * 但默认是关的。
 * <p>
 * <b>关着的时候回什么</b>：<b>真 HTTP 401</b> + {@code Result} 正文（业务码 401）。
 * 这一条与 publish 的历史口径（HTTP 200 + 业务 code:403）<b>不一样</b>，是有意为之：
 * publish 那个 200 早被 README 记成「关着时实测 HTTP 200 + 业务 code:403，不是 HTTP 403」，
 * 属于历史包袱；而这个端点是<b>新加</b>的，没有历史包袱可背，就按仓库里管理面闸门
 * （{@code AdminEndpointGate}）已经立好的口径来 —— 上游网关和监控看得到真状态码。
 * publish 自己那套口径<b>保持不变</b>（回归钉在 {@code MsgPublishGateDisabledTest} /
 * {@code MsgPublishGateEnabledTest}），不因为多了这个端点就顺手"统一"掉。
 * <p>
 * <b>机器对机器走另一个端点</b>：{@code POST /api/msg/open/send}（见 {@link MsgOpenSendController}）。
 * 本端点留在宿主 SSO 闸门内，页面上的人发消息不受影响。
 * <p>
 * <b>校验</b>（三条，都是 4xx + 人话）：
 * <ol>
 *   <li>channel 必须是已知通道；</li>
 *   <li>{@code receiver} 与 {@code userId} 至少给一个（站内信走 userId，短信/邮箱走 receiver）；</li>
 *   <li>{@code content} 非空。</li>
 * </ol>
 * 校验放在<b>门禁之后</b>：闸门关着的时候，任何 body 都只回 401 —— 端点关着的时候，
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
     * 401 正文。准入口径从「开关」改成「身份」：
     * AK 客户端（{@code X-Api-Key}）或平台登录态都能发；两者都没有才 401。
     */
    public static final String UNAUTHORIZED_MESSAGE = MsgDirectSender.UNAUTHORIZED_MESSAGE;

    /**
     * {@code bizType} 为空时的归档值（站内信落库那一列是 NOT NULL，空着会一路走到 INSERT 才炸）。
     */
    public static final String DEFAULT_BIZ_TYPE = MsgDirectSender.DEFAULT_BIZ_TYPE;

    @Resource
    private MsgDirectSender sender;

    /**
     * 直发一条消息。
     *
     * @param req     请求体（{@code Message} 的直填子集），可空
     * @param request 当前请求，用于解析调用者身份
     * @return 成功时 {@code data} 里带 {@code msgId}；失败时是带人话的 4xx/5xx
     */
    @PostMapping("/send")
    @BizApi(summary = "直发一条消息", description = "直发一条消息。", tags = { "z-msg" })
    public ResponseEntity<Result<Map<String, Object>>> send(@RequestBody(required = false) @BizParam(name = "req", type = BizField.FieldType.STRING, description = "请求体（{@code Message} 的直填子集），可空", position = BizParam.Position.BODY) MsgSendRequest req,
                                                             HttpServletRequest request) {
        return sender.sendForPlatform(req, request);
    }
}



