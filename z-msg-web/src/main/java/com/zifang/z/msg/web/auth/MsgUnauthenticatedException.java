package com.zifang.z.msg.web.auth;

/**
 * 身份解不出来。由 {@code MsgWebExceptionHandler} 统一映射成 401，
 * 这样受保护端点里不必每个都写一遍空值判断。
 */
public class MsgUnauthenticatedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MsgUnauthenticatedException(String message) {
        super(message);
    }
}
