package com.zifang.z.msg.example.inbox;

import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageGateway;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.example.demo.DemoAuthController;
import com.zifang.z.msg.web.auth.MsgPrincipalResolver;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * "系统站内信"在宿主侧的全部代码就是这一个方法：{@code gateway.send(IN_APP)}。
 * <p>
 * 落库、未读数、以及"在线就立刻推帧、离线就等下次拉"都不是这里写的，是
 * {@code InAppChannel} + {@code z-msg-ws} 在背后做的；这里只负责说"发给谁、发什么"。
 * <p>
 * 为什么不直接用 {@code POST /api/msg/publish}：那个端点默认 403，因为它能被任何人调用来
 * 往任意 userId 的收件箱里塞消息。演示里同样要做这件事，但身份是服务端从 session 解出来的，
 * 所以自己写一个受控入口才是示例该教的姿势。
 */
@RestController
@RequestMapping("/demo/notify")
public class DemoNotifyController {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Resource
    private MessageGateway gateway;
    @Resource
    private MsgPrincipalResolver principalResolver;

    /**
     * @param to 收信人 userId；省略则发给自己（两个人互相@时填对方）
     */
    @PostMapping
    public Result<MessageSendResult> notify(HttpServletRequest request,
                                            @RequestParam(required = false) Long to,
                                            @RequestParam(defaultValue = "有一条新消息") String title) {
        Long me = principalResolver.require(request);
        Long receiver = to == null ? me : to;
        if (!DemoAuthController.DEMO_USERS.containsKey(receiver)) {
            return Result.<MessageSendResult>fail("演示账号只有 1001 / 1002").code(400);
        }
        String from = String.valueOf(DemoAuthController.DEMO_USERS.get(me));
        Message message = Message.builder()
                .channel(Channels.IN_APP)
                .userId(receiver)
                .receiver(String.valueOf(receiver))
                .bizType("demo.notify")
                .subject(title)
                .content(from + " 在 " + LocalDateTime.now().format(HHMM) + " 说：" + title)
                .build();
        return Result.success(gateway.send(message));
    }
}
