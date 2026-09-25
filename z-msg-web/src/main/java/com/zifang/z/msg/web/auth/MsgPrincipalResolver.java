package com.zifang.z.msg.web.auth;

import javax.servlet.http.HttpServletRequest;

/**
 * 谁是当前调用者。z-msg 不做登录，只留这个接缝给宿主接自己的认证体系（z-ctc 的 JWT 等）。
 * <p>
 * 之所以必须有它：1.0.0 的所有收件箱/偏好接口都把 {@code userId} 当普通入参收，
 * 于是任何人改一下 query 就能读、删、改别人的消息。身份必须由服务端从凭证里解出来，
 * 客户端说什么不算。
 */
public interface MsgPrincipalResolver {

    /**
     * @return 已认证的 userId；未认证返回 null（不要抛异常，交给 {@link #require} 统一成 401）
     */
    Long resolve(HttpServletRequest request);

    /**
     * 受保护端点用这个。解不出身份就直接 401，而不是退回到"相信客户端传的 userId"。
     */
    default Long require(HttpServletRequest request) {
        Long userId = resolve(request);
        if (userId == null) {
            throw new MsgUnauthenticatedException("未识别到调用者身份");
        }
        return userId;
    }
}
