package com.zifang.z.msg.im.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.im.domain.entity.ImConversationDO;
import com.zifang.z.msg.im.domain.entity.ImMemberDO;
import com.zifang.z.msg.im.domain.model.ImConvTypes;
import com.zifang.z.msg.im.domain.model.ImConversationView;
import com.zifang.z.msg.im.domain.service.ImConversationService;
import com.zifang.z.msg.im.domain.service.ImMessageService;
import com.zifang.z.msg.web.auth.MsgPrincipalResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 会话与成员 API。
 * <p>
 * 两条硬规则，整个 {@code /api/msg/im/**} 都一样：
 * <ol>
 *   <li><b>调用者身份只来自 {@link MsgPrincipalResolver}</b>。请求体里出现的
 *       {@code userId} / {@code senderUserId} / {@code operator} 一律忽略
 *       （见 {@link #principal}）——1.0.0 的越权面就是"body 里写谁就是谁"；
 *       解不出身份由 web 层的 advice 统一转 401，这里不写判断；</li>
 *   <li><b>列表用 POST，分页参数 {@code page}(1 起) / {@code size}</b>：
 *       与会话/成员这类可能带长参数数组的查询保持一致形状。</li>
 * </ol>
 * <p>
 * 归属判定不在这里做：service 的每个方法 WHERE 里都带 {@code user_id}，
 * 越权不是"忘了判"而是"写不出"。
 */
@RestController
@RequestMapping("/api/msg/im/conversation")
public class ImConversationController {

    private static final Logger log = LogManager.getLogger(ImConversationController.class);

    @Resource
    private ImConversationService conversationService;
    @Resource
    private ImMessageService messageService;
    @Resource
    private MsgPrincipalResolver principalResolver;

    /**
     * 我的会话列表：带 {@code lastMsgSeq} / {@code lastMsgPreview} / {@code memberCount}
     * 与本人口径的 {@code unreadCount}。按最后一条消息的时间倒序。
     */
    @PostMapping("/list")
    public Result<IPage<ImConversationView>> list(HttpServletRequest request,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        Long me = principal(request, body);
        Map<String, Object> b = ImApiSupport.requireBody(body);
        int page = ImApiSupport.intWithDefault(b, "page", 1);
        int size = ImApiSupport.intWithDefault(b, "size", 0);
        return Result.success(conversationService.pageMine(me, page, size));
    }

    /**
     * 开（或取回）与某人之间的单聊。并发点同一个对端只会得到一个会话，靠 {@code uk_pair}。
     */
    @PostMapping("/single")
    public Result<ImConversationDO> single(HttpServletRequest request,
                                           @RequestBody(required = false) Map<String, Object> body) {
        Long me = principal(request, body);
        Map<String, Object> b = ImApiSupport.requireBody(body);
        Long peer = ImApiSupport.id(b, "peerUserId");
        return Result.success(conversationService.single(me, peer, ImApiSupport.string(b, "tenantCode")));
    }

    /**
     * 建群聊（{@code convType=GROUP}，默认）或聊天室（{@code ROOM}）。
     * 发起人一定进成员表并且是 OWNER。
     */
    @PostMapping("/group")
    public Result<ImConversationDO> group(HttpServletRequest request,
                                          @RequestBody(required = false) Map<String, Object> body) {
        Long me = principal(request, body);
        Map<String, Object> b = ImApiSupport.requireBody(body);
        String type = ImApiSupport.string(b, "convType");
        if (type == null || type.trim().isEmpty()) {
            type = ImConvTypes.GROUP;
        }
        return Result.success(conversationService.createGroup(me,
                ImApiSupport.idList(b, "memberUserIds"),
                ImApiSupport.string(b, "title"),
                ImApiSupport.string(b, "avatar"),
                ImApiSupport.string(b, "tenantCode"),
                type));
    }

    @PostMapping("/members")
    public Result<List<ImMemberDO>> members(HttpServletRequest request,
                                            @RequestBody(required = false) Map<String, Object> body) {
        Long me = principal(request, body);
        Long conversationId = ImApiSupport.id(ImApiSupport.requireBody(body), "conversationId");
        return Result.success(conversationService.members(me, conversationId));
    }

    @PostMapping("/member/add")
    public Result<List<ImMemberDO>> addMembers(HttpServletRequest request,
                                               @RequestBody(required = false) Map<String, Object> body) {
        Long me = principal(request, body);
        Map<String, Object> b = ImApiSupport.requireBody(body);
        return Result.success(conversationService.addMembers(me,
                ImApiSupport.id(b, "conversationId"), ImApiSupport.idList(b, "userIds")));
    }

    /**
     * 踢人。返回真被删掉的行数（不存在的人算 0，不报错）。
     */
    @PostMapping("/member/remove")
    public Result<Integer> removeMembers(HttpServletRequest request,
                                         @RequestBody(required = false) Map<String, Object> body) {
        Long me = principal(request, body);
        Map<String, Object> b = ImApiSupport.requireBody(body);
        return Result.success(conversationService.removeMembers(me,
                ImApiSupport.id(b, "conversationId"), ImApiSupport.idList(b, "userIds")));
    }

    /**
     * 改角色（含转让群主）。只有 OWNER 可以调。
     */
    @PostMapping("/member/role")
    public Result<ImMemberDO> setRole(HttpServletRequest request,
                                      @RequestBody(required = false) Map<String, Object> body) {
        Long me = principal(request, body);
        Map<String, Object> b = ImApiSupport.requireBody(body);
        return Result.success(conversationService.setRole(me, ImApiSupport.id(b, "conversationId"),
                ImApiSupport.id(b, "userId"), ImApiSupport.string(b, "role")));
    }

    @PostMapping("/leave")
    public Result<Boolean> leave(HttpServletRequest request,
                                 @RequestBody(required = false) Map<String, Object> body) {
        Long me = principal(request, body);
        return Result.success(conversationService.leave(me,
                ImApiSupport.id(ImApiSupport.requireBody(body), "conversationId")));
    }

    /**
     * 免打扰：仍然计未读，只是不弹。
     */
    @PostMapping("/mute")
    public Result<Boolean> mute(HttpServletRequest request,
                               @RequestBody(required = false) Map<String, Object> body) {
        Long me = principal(request, body);
        Map<String, Object> b = ImApiSupport.requireBody(body);
        return Result.success(conversationService.mute(me, ImApiSupport.id(b, "conversationId"),
                ImApiSupport.bool(b, "muted", true)));
    }

    /**
     * 清空会话：只把自己的 {@code cleared_seq} 推到当前水位，一行都不删。
     * 别人的历史、以及将来新成员看到的上下文都不受影响。
     *
     * @return 推到的位置（seq）
     */
    @PostMapping("/clear")
    public Result<Long> clear(HttpServletRequest request,
                              @RequestBody(required = false) Map<String, Object> body) {
        Long me = principal(request, body);
        Long conversationId = ImApiSupport.id(ImApiSupport.requireBody(body), "conversationId");
        return Result.success(messageService.clear(conversationId, me));
    }

    /**
     * 身份只从凭证里解。请求体里自报的 {@code userId} 一类字段一律不用，
     * 出现了就在日志里点一句（便于发现前端还在按 1.0.0 的形状发）。
     */
    private Long principal(HttpServletRequest request, Map<String, Object> body) {
        if (body != null && (body.containsKey("userId") || body.containsKey("operatorUserId")
                || body.containsKey("senderUserId"))) {
            log.warn("[z-msg-im] 请求体里带了自报身份字段，已忽略（身份只来自登录凭证）: keys={}",
                    body.keySet());
        }
        return principalResolver.require(request);
    }
}
