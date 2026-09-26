package com.zifang.z.msg.example.demo;

import com.zifang.z.msg.web.auth.MsgPrincipalResolver;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * 演示身份接缝：从服务端 session 里取 userId。
 * <p>
 * z-msg 自己不做登录，只留 {@code MsgPrincipalResolver} 这一个口子。这里之所以用 session
 * 而不是"读一个请求头里的 userId"：那正是 1.0.0 越权读别人收件箱的成因，也是
 * {@code TrustedHeaderPrincipalResolver} 默认关掉的原因。宿主接 z-ctc 的 JWT 时，
 * 换的就是这一个 bean —— 其余 z-msg 代码不用碰。
 * <p>
 * {@value DemoAuthController#TAB_HEADER} 头看起来像"信了客户端一个头"，其实不是：它的值不是
 * 身份，而是一把由页面自己随机生成、存在 {@code sessionStorage} 里的<strong>不透明句柄</strong>，
 * 身份（{@code 1001/1002}）只在服务端 {@code /demo/login} 时写进这张表。作用域也仅限"让同一个
 * 浏览器的两个标签分别是两个人"（见 {@link DemoAuthController} 的原因说明）。带着一个没登记过的
 * tab 号来 = 查不到 = 按未登录处理，而不是退回 cookie 冒充别人。
 * <p>
 * 注意 {@code /api/msg/inbox/ws-token} 也走这个 resolver，所以 WebSocket 握手用的短期 ticket
 * 只能被已经登录的身份换到；浏览器在握手时带不上自定义头这件事，正是 ticket 存在的理由。
 */
@Component
public class DemoSessionPrincipalResolver implements MsgPrincipalResolver {

    public static final String SESSION_KEY = "demo.userId";

    @Override
    public Long resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String tab = request.getHeader(DemoAuthController.TAB_HEADER);
        if (tab != null && !tab.trim().isEmpty()) {
            return DemoAuthController.TAB_USERS.get(tab.trim());
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SESSION_KEY);
        return value instanceof Long ? (Long) value : null;
    }
}
