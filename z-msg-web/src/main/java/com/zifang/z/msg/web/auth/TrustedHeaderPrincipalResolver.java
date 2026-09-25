package com.zifang.z.msg.web.auth;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletRequest;

/**
 * 缺省实现：只信一个由**可信入口**（网关 / nginx / 宿主自己的 filter）注入的请求头。
 * <p>
 * {@code z-msg.web.trusted-header-enabled} 默认 false —— 也就是默认谁都不信、所有受保护端点都 401。
 * 这不是保守，是正确：如果默认就认 {@code X-Msg-User-Id}，而宿主没在网关上剥掉客户端自带的同名头，
 * 任何调用方都能自称别人，1.0.0 的越权只是换了个位置。
 * <p>
 * 生产要么显式打开这个头（并确保上游覆盖它），要么注册自己的 {@link MsgPrincipalResolver} bean。
 */
public class TrustedHeaderPrincipalResolver implements MsgPrincipalResolver {

    private static final Logger log = LogManager.getLogger(TrustedHeaderPrincipalResolver.class);

    private final boolean trustedHeaderEnabled;
    private final String headerName;

    private volatile boolean warned;

    public TrustedHeaderPrincipalResolver(boolean trustedHeaderEnabled, String headerName) {
        this.trustedHeaderEnabled = trustedHeaderEnabled;
        this.headerName = headerName == null || headerName.trim().isEmpty()
                ? "X-Msg-User-Id" : headerName.trim();
    }

    @Override
    public Long resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        if (!trustedHeaderEnabled) {
            warnOnce();
            return null;
        }
        String raw = request.getHeader(headerName);
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void warnOnce() {
        if (warned) {
            return;
        }
        warned = true;
        log.error("[z-msg] 没有可用的 MsgPrincipalResolver：z-msg.web.trusted-header-enabled=false。"
                + "收件箱/偏好类端点将一律 401。请注册宿主自己的 resolver bean，"
                + "或在确认上游网关会覆盖该头后打开 trusted-header-enabled");
    }
}
