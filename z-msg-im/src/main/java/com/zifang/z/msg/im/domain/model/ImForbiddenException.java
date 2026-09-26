package com.zifang.z.msg.im.domain.model;

/**
 * "这个人不能碰这个会话"。由 {@code ImApiExceptionHandler} 统一映射成 403。
 * <p>
 * 单独一个类型而不是抛 {@link IllegalArgumentException}：后者会被 web 层的兜底 advice
 * 转成 400，而"参数没问题、但你没资格"和"参数不合法"在客户端处理上完全不同
 * （前者该跳登录/退群，后者该改请求）。
 */
public class ImForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ImForbiddenException(String message) {
        super(message);
    }

    /**
     * 不存在的会话与无权访问的会话回同一<strong>句式</strong>：只回显调用方自己给的那个
     * 会话 id，不说"存在/不存在"这两个字。这样拿一个探测不到的 id 去问，错误文本里
     * 没有任何它先前不知道的信息，403 也就成不了枚举会话 id 的 oracle。
     */
    public static ImForbiddenException notMember(Long conversationId, Long userId) {
        return new ImForbiddenException("无权访问会话 " + conversationId);
    }
}
