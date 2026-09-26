package com.zifang.z.msg.im.api;

import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.im.domain.entity.ImReadReceiptDO;
import com.zifang.z.msg.im.domain.service.ImReadService;
import com.zifang.z.msg.web.auth.MsgPrincipalResolver;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 已读与未读 API。
 * <p>
 * {@code lastReadSeq} 可以是客户端以为的水位，但服务端两头都封：
 * 只前进不回退（迟到的旧回执改不动新值），且以会话 {@code last_msg_seq} 封顶
 * （谎报"我读到 999"不能把还没发的消息算成已读）。返回值是服务端的真实游标，
 * 客户端据此纠正本地 UI。
 */
@RestController
@RequestMapping("/api/msg/im/read")
public class ImReadController {

    @Resource
    private ImReadService readService;
    @Resource
    private MsgPrincipalResolver principalResolver;

    /**
     * 标已读。不传 {@code lastReadSeq} 就是"读到会话最新"。
     */
    @PostMapping("/mark")
    public Result<Map<String, Object>> mark(HttpServletRequest request,
                                            @RequestBody(required = false) Map<String, Object> body) {
        Long me = principalResolver.require(request);
        Map<String, Object> b = ImApiSupport.requireBody(body);
        long upto = ImApiSupport.numberWithDefault(b, "lastReadSeq", Long.MAX_VALUE);
        long effective = readService.markRead(ImApiSupport.id(b, "conversationId"), me, upto);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("lastReadSeq", effective);
        data.put("unreadCount", readService.unread(ImApiSupport.id(b, "conversationId"), me));
        return Result.success(data);
    }

    /**
     * 一个会话的未读数。
     */
    @PostMapping("/unread")
    public Result<Long> unread(HttpServletRequest request,
                               @RequestBody(required = false) Map<String, Object> body) {
        Long me = principalResolver.require(request);
        return Result.success(readService.unread(
                ImApiSupport.id(ImApiSupport.requireBody(body), "conversationId"), me));
    }

    /**
     * 未读汇总：每个会话一条 + 总数。前端红点只需要一次调用。
     */
    @PostMapping("/summary")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> summary(HttpServletRequest request,
                                              @RequestBody(required = false) Map<String, Object> body) {
        Long me = principalResolver.require(request);
        return Result.success(readService.unreadSummary(me));
    }

    /**
     * "谁读到哪"。只有同会话成员读得到——已读状态本身是社交信息，
     * 不能拿一个会话 id 就来枚举别人的阅读进度。
     */
    @PostMapping("/receipts")
    public Result<List<ImReadReceiptDO>> receipts(HttpServletRequest request,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        Long me = principalResolver.require(request);
        return Result.success(readService.receipts(
                ImApiSupport.id(ImApiSupport.requireBody(body), "conversationId"), me));
    }
}
