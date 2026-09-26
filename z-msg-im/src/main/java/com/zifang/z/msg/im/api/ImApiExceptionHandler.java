package com.zifang.z.msg.im.api;

import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.im.domain.model.ImForbiddenException;
import com.zifang.z.msg.im.domain.model.ImNotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * IM 自己的两类错误出口。
 * <p>
 * 三条克制：
 * <ul>
 *   <li>{@code @Order(0)} 排在 {@code MsgWebExceptionHandler}（无序号，即最低优先级）之前，
 *       否则 {@code ImForbiddenException} 会先被那里的 {@code Exception.class} 兜底吞成 500；</li>
 *   <li>只声明这两个类型，别的一律不接 —— 站内信那套 400/401/500 的映射继续由 web 层负责，
 *       这里不越权重写；</li>
 *   <li>错误文案原样回给客户端（是本模块自己写的固定句子，不含 SQL、不含堆栈），
 *       堆栈只进日志。</li>
 * </ul>
 */
@RestControllerAdvice
@Order(0)
public class ImApiExceptionHandler {

    private static final Logger log = LogManager.getLogger(ImApiExceptionHandler.class);

    @ExceptionHandler(ImForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> forbidden(ImForbiddenException e) {
        log.warn("[z-msg-im] 拒绝: {}", e.getMessage());
        return Result.<Void>fail(e.getMessage()).code(403);
    }

    @ExceptionHandler(ImNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> notFound(ImNotFoundException e) {
        log.warn("[z-msg-im] 未找到: {}", e.getMessage());
        return Result.<Void>fail(e.getMessage()).code(404);
    }
}
