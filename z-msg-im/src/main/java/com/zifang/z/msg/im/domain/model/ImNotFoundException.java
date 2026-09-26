package com.zifang.z.msg.im.domain.model;

/**
 * 资源确实不存在（不是无权）。映射成 404。
 * <p>
 * 和 {@link ImForbiddenException} 分开的唯一理由：会话是调用方自己创建的、
 * 事后被删除这类场景需要区分"我建过但没了"和"从来不是我家的"。
 * 成员判定一律走 403，不给探测。
 */
public class ImNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ImNotFoundException(String message) {
        super(message);
    }
}
