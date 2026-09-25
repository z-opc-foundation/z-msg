package com.zifang.z.msg.web.api;

import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.api.MessageBus;
import com.zifang.z.msg.api.MessageEvent;
import com.zifang.z.msg.core.domain.service.MsgDeliveryLogService;
import com.zifang.z.msg.web.config.MsgWebProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息总线入口 + 投递概览。
 * <p>
 * 1.0.x 的收件箱读写接口（{@code /list}、{@code /read/{id}}、{@code /delete-all/{userId}}、
 * {@code /detail/{id}}、{@code /unread/{userId}}）都散在这个类里，且把 {@code userId} 当普通入参，
 * 于是任何人改一下参数就能读、标已读、甚至**物理清空任意用户的收件箱**
 * （原 {@code /delete-all} 走的是 {@code deleteById} 语义，没有软删、没有归属校验）。
 * <p>
 * 1.1.0 把这些能力整体挪到 {@link MsgInboxController}（{@code /api/msg/inbox/*}：身份由服务端解出，
 * 归属判断进 WHERE，删除是软删），旧路径不再保留 —— 留着一个能清空别人收件箱的 URL，
 * 比让调用方改一行路径贵得多。
 */
@RestController
@RequestMapping("/api/msg")
public class MessageController {

    @Resource
    private MessageBus messageBus;
    @Resource
    private MsgDeliveryLogService deliveryLogService;
    @Resource
    private MsgWebProperties webProperties;

    /**
     * 发一个业务事件（站内信通道会落库）。
     * <p>
     * 默认关闭：它能往**任意** userId 的收件箱里塞消息，等于一个未认证的钓鱼入口。
     * 这个端点本来是给服务间调用用的，宿主确认调用方在网络边界内之后，
     * 用 {@code z-msg.web.publish-endpoint-enabled=true} 打开。
     */
    @PostMapping("/publish")
    public Result<String> publish(@RequestParam String eventType, @RequestParam Long userId,
                                  @RequestBody(required = false) Map<String, Object> params) {
        if (!webProperties.isPublishEndpointEnabled()) {
            return Result.<String>fail("publish 端点未启用"
                    + "（确认调用方在网络边界内后设 z-msg.web.publish-endpoint-enabled=true）").code(403);
        }
        messageBus.publish(new MessageEvent(eventType, userId, params));
        return Result.success("ok");
    }

    @GetMapping("/delivery/stats")
    public Result<Map<String, Long>> deliveryStats() {
        Map<String, Long> stats = new HashMap<String, Long>();
        Long success = deliveryLogService.countSuccess(null, null, null);
        Long failed = deliveryLogService.countFailed(null, null, null);
        stats.put("success", success);
        stats.put("failed", failed);
        stats.put("total", (success == null ? 0L : success.longValue())
                + (failed == null ? 0L : failed.longValue()));
        return Result.success(stats);
    }
}
