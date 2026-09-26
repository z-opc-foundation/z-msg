package com.zifang.z.msg.example.demo;

import com.zifang.util.core.meta.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 演示用"登录"。两个固定账号，点一下就换人——为的是能在两个浏览器标签里分别当张三和李四，
 * 真看到一条消息从一个人飞进另一个人的窗口。
 * <p>
 * 路径刻意不带 {@code /api/msg} 前缀：这是宿主自己的认证，不属于 z-msg。z-msg 只消费
 * {@link DemoSessionPrincipalResolver} 解出来的身份。
 * <p>
 * <b>为什么除了 session 还要一把 tab</b>：一个浏览器只有一个 cookie jar，两个标签页带的是
 * 同一个 {@code JSESSIONID}，所以"标签 1 登 1001、标签 2 登 1002"实测会把标签 1 的身份一起改掉
 * （同一个 jar 里先登 1001 未读是 2，再登 1002 就变成 1）。而页面提示写的是"开两个标签页"，
 * 所以这里改成按 tab 记身份：{@code sessionStorage} 恰好是<strong>每个标签一份</strong>的，
 * 页面把自己的 tab 号放进 {@value #TAB_HEADER} 头，服务端按这个头认人。
 * cookie 那条路一并保留（不带 tab 头时照旧走 session），宿主接真认证时替换的是
 * {@link DemoSessionPrincipalResolver} 这一个 bean，两处都不是 z-msg 的代码。
 */
@RestController
public class DemoAuthController {

    /** 演示账号：userId -> 昵称。昵称进消息内容里，方便肉眼认出是谁发的 */
    public static final Map<Long, String> DEMO_USERS = demoUsers();

    /** 每个浏览器标签一个值，页面存在 {@code sessionStorage} 里（它天生按标签隔离） */
    public static final String TAB_HEADER = "X-Demo-Tab";

    /**
     * tab -&gt; userId。只有两个演示账号、条目数等于开过的标签数，所以不设过期；
     * 这不是通用会话存储，别照着它写生产。
     */
    static final ConcurrentHashMap<String, Long> TAB_USERS = new ConcurrentHashMap<String, Long>();

    private static Map<Long, String> demoUsers() {
        Map<Long, String> users = new HashMap<Long, String>();
        users.put(Long.valueOf(1001L), "张三");
        users.put(Long.valueOf(1002L), "李四");
        return java.util.Collections.unmodifiableMap(users);
    }

    @PostMapping("/demo/login")
    public Result<Map<String, Object>> login(HttpServletRequest request, @RequestParam long as,
                                             @RequestParam(value = "tab", required = false) String tab) {
        Long userId = Long.valueOf(as);
        if (!DEMO_USERS.containsKey(userId)) {
            return Result.<Map<String, Object>>fail("演示账号只有 1001 / 1002").code(400);
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(DemoSessionPrincipalResolver.SESSION_KEY, userId);
        String tabId = tabOf(request, tab);
        if (tabId != null) {
            TAB_USERS.put(tabId, userId);
        }
        return Result.success(who(userId));
    }

    @PostMapping("/demo/logout")
    public Result<Boolean> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        String tabId = tabOf(request, null);
        if (tabId != null) {
            TAB_USERS.remove(tabId);
        }
        return Result.success(Boolean.TRUE);
    }

    /** tab 号只从头或 query 里取，取不到就 null（那就退回按 cookie 认人）。 */
    private static String tabOf(HttpServletRequest request, String fromParam) {
        String tab = request.getHeader(TAB_HEADER);
        if (tab == null || tab.trim().isEmpty()) {
            tab = fromParam;
        }
        return tab == null || tab.trim().isEmpty() ? null : tab.trim();
    }

    private Map<String, Object> who(Long userId) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("userId", userId);
        data.put("nickname", DEMO_USERS.get(userId));
        return data;
    }
}
