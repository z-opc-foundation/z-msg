package com.zifang.z.msg.im.api;

import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.im.domain.entity.ImMessageDO;
import com.zifang.z.msg.im.domain.model.ImForbiddenException;
import com.zifang.z.msg.im.domain.model.ImNotFoundException;
import com.zifang.z.msg.im.domain.service.ImMessageService;
import com.zifang.z.msg.web.auth.MsgPrincipalResolver;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 发消息与增量拉历史。
 * <p>
 * 发送者身份取登录态，<b>不接受 body 里的 {@code senderUserId}</b>；
 * 不是会话成员 → 403（与"会话不存在"同一个形状，不给枚举会话的 oracle）。
 */
@RestController
@RequestMapping("/api/msg/im/message")
public class ImMessageController {

    @Resource
    private ImMessageService messageService;
    @Resource
    private MsgPrincipalResolver principalResolver;

    /**
     * 发消息。
     * <ul>
     *   <li>{@code clientMsgId} 同一个值在同一会话里重复提交，返回同一条消息（不新增行）；</li>
     *   <li>返回体里的 {@code seq} 是会话内严格递增的水位，也是实时帧上的 seq，
     *       客户端拿它当增量拉取的游标。</li>
     * </ul>
     */
    @PostMapping("/send")
    public Result<ImMessageDO> send(HttpServletRequest request,
                                    @RequestBody(required = false) Map<String, Object> body) {
        Long me = principalResolver.require(request);
        Map<String, Object> b = ImApiSupport.requireBody(body);
        ImMessageDO sent = messageService.send(ImApiSupport.id(b, "conversationId"), me,
                ImApiSupport.string(b, "msgType"), ImApiSupport.string(b, "content"),
                ImApiSupport.string(b, "clientMsgId"), ImApiSupport.idList(b, "atUserIds"),
                ImApiSupport.optionalId(b, "replyToSeq"));
        return Result.success(sent);
    }

    /**
     * 增量拉历史：{@code seq > max(sinceSeq, 本人 cleared_seq)}，升序，最多 size 条。
     * 清空过的会话从这里读不到清空点以前的消息，但消息行并没有被删。
     */
    @PostMapping("/history")
    public Result<List<ImMessageDO>> history(HttpServletRequest request,
                                             @RequestBody(required = false) Map<String, Object> body) {
        Long me = principalResolver.require(request);
        Map<String, Object> b = ImApiSupport.requireBody(body);
        long sinceSeq = ImApiSupport.numberWithDefault(b, "sinceSeq", 0L);
        // size 不给就不填 0，由 service 落到 z-msg.im.default-page-size
        int size = ImApiSupport.intWithDefault(b, "size", 0);
        return Result.success(messageService.history(ImApiSupport.id(b, "conversationId"), me, sinceSeq, size));
    }

    /**
     * 按 seq 取一条（点"回复"气泡时反查原文）。被自己清空掉的区间读不到 ——
     * 与 {@code /history} 同一个可见性口径，两处不一致客户端就会算错账。
     */
    @PostMapping("/at")
    public Result<ImMessageDO> at(HttpServletRequest request,
                                  @RequestBody(required = false) Map<String, Object> body) {
        Long me = principalResolver.require(request);
        Map<String, Object> b = ImApiSupport.requireBody(body);
        ImMessageDO row = messageService.bySeq(ImApiSupport.id(b, "conversationId"), me,
                ImApiSupport.numberWithDefault(b, "seq", -1L));
        if (row == null) {
            // 与"不存在"同形：不给"这条被清空了 vs 根本没有"这种探测面
            throw new ImNotFoundException("消息不存在");
        }
        return Result.success(row);
    }

    /**
     * 会话水位（{@code last_msg_seq}）：客户端连上 {@code room:} 之前先对齐一次游标，
     * 避免"订阅成功后漏掉订阅前那几帧"要靠拉全量历史来补。
     */
    @PostMapping("/head")
    public Result<Long> head(HttpServletRequest request,
                             @RequestBody(required = false) Map<String, Object> body) {
        Long me = principalResolver.require(request);
        Long conversationId = ImApiSupport.id(ImApiSupport.requireBody(body), "conversationId");
        return Result.success(messageService.headSeq(conversationId, me));
    }
}
