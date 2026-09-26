package com.zifang.z.msg.web.config;

import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.core.json.MsgJson;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 管理面闸门：{@code z-msg.web.admin-endpoints-enabled} 不为 true 时，模板 / 批量 / 投递日志
 * 三组前缀一律 HTTP 403（不是"业务码 200 + body code 403"—— 这些请求根本没进 controller，
 * 拿真实状态码回给上游网关和监控才看得见）。
 * <p>
 * 为什么是拦截器而不是每个 controller 里写一遍 if：管理面本来就没有身份概念，
 * 靠"每个作者都记得抄那段 if"来兜底，等于在下一个 controller 上加回来同一个洞。
 * 前缀清单只有 {@link #ADMIN_PREFIXES} 一处，{@code MsgAdminEndpointGateTest} 拿活的
 * handler mapping 逐个路径过一遍 {@link #isAdminPath}，钉住"拦住了哪些、放过了哪些"，
 * 因此新增管理面既不能漏登记前缀（会红），也不能悄悄从闸门下走出去（也会红）。
 */
public class AdminEndpointGate implements HandlerInterceptor {

    /** 管理面前缀。加新的一组就往这里加。 */
    public static final Set<String> ADMIN_PREFIXES =
            Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
                    "/api/msg/template", "/api/msg/batch", "/api/msg/delivery")));

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    /** 403 正文里的这句话同时是给宿主看的开法说明。 */
    public static final String DISABLED_MESSAGE =
            "z-msg 管理面未启用：确认调用方已在认证边界内后设置 z-msg.web.admin-endpoints-enabled=true";

    private final MsgWebProperties webProperties;

    public AdminEndpointGate(MsgWebProperties webProperties) {
        this.webProperties = webProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (webProperties.isAdminEndpointsEnabled() || !isAdminRequest(request)) {
            return true;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(MsgJson.toJson(
                Result.<String>fail(DISABLED_MESSAGE).code(HttpStatus.FORBIDDEN.value())));
        return false;
    }

    /**
     * 判路径就够了：管理面 controller 的映射本身全部以 {@link #ADMIN_PREFIXES} 开头，
     * 而 Spring 用来路由的那条 lookup path 与此处是同一次解码+cleanPath 的结果
     * （{@code getPathWithinApplication} 会先 decode、去掉 {@code ;matrix} 段、再收拾 {@code ..}），
     * 所以"原始路径不像管理面、却被路由进管理面 controller"这种形状在这里出不来。
     * 反过来再比一次 Spring 匹配到的 pattern 只会多出一个可能误伤宿主自己路由的判断，测不出新东西。
     */
    static boolean isAdminRequest(HttpServletRequest request) {
        return isAdminPath(PATH_HELPER.getPathWithinApplication(request));
    }

    /** 前缀按路径段对齐：{@code /api/msg/templates} 不是管理面，裸 {@code /api/msg/template} 是。 */
    public static boolean isAdminPath(String path) {
        if (path == null) {
            return false;
        }
        for (String prefix : ADMIN_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
