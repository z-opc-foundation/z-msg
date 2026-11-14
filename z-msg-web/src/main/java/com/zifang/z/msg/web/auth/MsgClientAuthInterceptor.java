package com.zifang.z.msg.web.auth;

import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.core.domain.entity.MsgClientDO;
import com.zifang.z.msg.core.domain.service.MsgClientService;
import com.zifang.z.msg.core.json.MsgJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 客户端 AK 鉴权拦截器（对外调用方：别的模块 / 脚本 / 外部系统）
 * <p>
 * <b>为什么要有它</b>：z-msg 与别的板块之间只有 HTTP 边界，调用方要发消息只能打
 * {@code POST /api/msg/send}。那个端点原本只有「布尔开关」一种准入，开着就是未认证投递入口；
 * 现在补一条凭据路：调用方带 {@code X-Api-Key}，服务端校验后把客户端身份放进
 * {@link #CLIENT_ATTRIBUTE}，端点据此放行。
 * <p>
 * <b>不带这个头就放行</b>（不是 401）：端点自己还要接平台用户身份（页面上给人发消息走
 * 平台 SSO 的 Bearer），所以「有没有 AK」不是端点准入的充分条件。带了但不对，才 401/403 ——
 * 免得有人拿着把废 AK 进来，被一条含糊的 401 挡住却不知道自己 AK 过期了。
 * <p>
 * <b>AK 明文不落库</b>：库里只有 sha-256 摘要，校验时对来头做同样摘要再比对。
 */
public class MsgClientAuthInterceptor implements HandlerInterceptor {

    /** 校验通过的客户端身份放在这个 request attribute 里。 */
    public static final String CLIENT_ATTRIBUTE = "z.msg.client";

    public static final String HEADER_API_KEY = "X-Api-Key";

    private static final Logger log = LogManager.getLogger(MsgClientAuthInterceptor.class);

    private final MsgClientService clientService;

    public MsgClientAuthInterceptor(MsgClientService clientService) {
        this.clientService = clientService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String apiKey = request.getHeader(HEADER_API_KEY);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            // 没有 AK：交给端点自己的准入（平台用户身份 / 老开关）
            return true;
        }

        MsgClientDO client = clientService.findByPlainKey(apiKey);
        if (client == null) {
            writeError(response, HttpStatus.UNAUTHORIZED, "INVALID_API_KEY", "API Key 不存在");
            return false;
        }
        if (client.getStatus() == null || client.getStatus() != 1) {
            writeError(response, HttpStatus.UNAUTHORIZED, "API_KEY_DISABLED", "API Key 已禁用");
            return false;
        }
        if (client.getExpireTime() != null && client.getExpireTime().isBefore(java.time.LocalDateTime.now())) {
            writeError(response, HttpStatus.UNAUTHORIZED, "API_KEY_EXPIRED", "API Key 已过期");
            return false;
        }
        if (!clientService.verifyIp(client, remoteIp(request))) {
            writeError(response, HttpStatus.FORBIDDEN, "IP_NOT_ALLOWED",
                    "IP " + remoteIp(request) + " 不在白名单中");
            return false;
        }
        if (!clientService.consumeQuota(client)) {
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED",
                    "客户端 " + client.getAppCode() + " 当日配额已用尽");
            return false;
        }

        request.setAttribute(CLIENT_ATTRIBUTE, client);
        return true;
    }

    /**
     * 从 request 里取已认证的客户端身份；没有就是 null（不是 AK 调用的）。
     */
    public static MsgClientDO clientOf(HttpServletRequest request) {
        Object attr = request.getAttribute(CLIENT_ATTRIBUTE);
        return attr instanceof MsgClientDO ? (MsgClientDO) attr : null;
    }

    private static String remoteIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            return first.trim();
        }
        return request.getRemoteAddr();
    }

    private static void writeError(HttpServletResponse response, HttpStatus status,
                                   String code, String message) throws IOException {
        log.warn("[MsgClientAuth] {} {}", code, message);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(MsgJson.toJson(
                Result.<String>fail(code + "：" + message).code(status.value())));
    }
}
