package com.zifang.z.msg.web.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.core.domain.entity.InAppMessage;
import com.zifang.z.msg.core.domain.service.MsgInboxService;
import com.zifang.z.msg.core.realtime.RealtimeTicketService;
import com.zifang.z.msg.web.auth.MsgPrincipalResolver;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 收件箱 API（对齐 feature043 里已写定的 {@code /api/msg/inbox/*} 契约）。
 * <p>
 * 这一层没有 {@code userId} 参数：身份只从 {@link MsgPrincipalResolver} 取，
 * 归属判断在 {@link MsgInboxService} 的 WHERE 里。解不出身份一律 401（由
 * {@code MsgWebExceptionHandler} 统一转），不会退化成"相信请求里写的 userId"。
 */
@RestController
@RequestMapping("/api/msg/inbox")
public class MsgInboxController {

    @Resource
    private MsgInboxService inboxService;
    @Resource
    private MsgPrincipalResolver principalResolver;
    @Resource
    private RealtimeTicketService ticketService;
    @Resource
    private com.zifang.z.msg.core.config.MessageProperties properties;

    @GetMapping("/list")
    public Result<IPage<InAppMessage>> list(HttpServletRequest request,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "0") int size,
                                            @RequestParam(required = false) Boolean unreadOnly,
                                            @RequestParam(required = false) String msgType) {
        Long me = principalResolver.require(request);
        return Result.success(inboxService.pageMine(me, page, size, unreadOnly, msgType));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount(HttpServletRequest request) {
        Long me = principalResolver.require(request);
        return Result.success(inboxService.unreadCount(me));
    }

    @GetMapping("/detail")
    public Result<InAppMessage> detail(HttpServletRequest request, @RequestParam Long id) {
        Long me = principalResolver.require(request);
        InAppMessage row = inboxService.detail(me, id);
        // 不是自己的和不存在的一律回同一个话，不给探测别人收件箱的 oracle
        return row != null ? Result.success(row)
                : Result.<InAppMessage>fail("消息不存在").code(404);
    }

    @PostMapping("/read")
    public Result<Boolean> read(HttpServletRequest request, @RequestParam Long id) {
        Long me = principalResolver.require(request);
        return Result.success(inboxService.markRead(me, id));
    }

    @PostMapping("/read-all")
    public Result<Integer> readAll(HttpServletRequest request) {
        Long me = principalResolver.require(request);
        return Result.success(inboxService.markAllRead(me));
    }

    @DeleteMapping("/delete")
    public Result<Boolean> delete(HttpServletRequest request, @RequestParam Long id) {
        Long me = principalResolver.require(request);
        return Result.success(inboxService.softDelete(me, id));
    }

    /**
     * 清空收件箱是软删；{@code onlyRead=true} 时只清已读的，避免一键抹掉未读历史。
     */
    @DeleteMapping("/delete-all")
    public Result<Integer> deleteAll(HttpServletRequest request,
                                     @RequestParam(defaultValue = "true") boolean onlyRead) {
        Long me = principalResolver.require(request);
        return Result.success(inboxService.softDeleteAll(me, onlyRead));
    }

    /**
     * 换一次 WebSocket 握手用的短期 ticket。
     * <p>
     * 浏览器 WebSocket 不能带 Authorization 头，而把长期 JWT 拼进 URL 会落进 access log、
     * 代理日志和浏览器历史。所以前端拿这个 60 秒 ticket 去连 {@code /api/msg/ws?token=...}。
     */
    @GetMapping("/ws-token")
    public Result<Map<String, Object>> wsToken(HttpServletRequest request) {
        Long me = principalResolver.require(request);
        String token = ticketService.issue(me);
        if (token == null) {
            return Result.<Map<String, Object>>fail(
                    "服务端未配置 z-msg.realtime.ticket-secret，无法签发 ws-ticket").code(503);
        }
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("token", token);
        data.put("expiresInSeconds", Math.max(1, properties.getRealtime().getTicketTtlSeconds()));
        return Result.success(data);
    }
}
