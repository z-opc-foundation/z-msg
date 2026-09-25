package com.zifang.z.msg.web.config;

import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.api.MessageException;
import com.zifang.z.msg.web.auth.MsgUnauthenticatedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一出口。
 * <p>
 * 之前 controller 里漏出来的异常由 Spring 的默认错误页兜住，返回体形状不是
 * {@code {code,msg,data,success}}，前端只能靠 try/catch 猜；更糟的是厂商 SDK 抛出的
 * 原始异常文本（含 URL、有时含带签名的 query）会直接进响应体。这里三类分开处理，
 * 兜底那条只回一句人话，堆栈只进日志。
 */
@RestControllerAdvice
public class MsgWebExceptionHandler {

    private static final Logger log = LogManager.getLogger(MsgWebExceptionHandler.class);

    @ExceptionHandler(MsgUnauthenticatedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> unauthenticated(MsgUnauthenticatedException e) {
        return Result.<Void>fail(e.getMessage()).code(401);
    }

    @ExceptionHandler({MessageException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> badRequest(Exception e) {
        log.warn("[z-msg] 请求不合法: {}", e.toString());
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return Result.<Void>fail(msg).code(400);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> unexpected(Exception e) {
        log.error("[z-msg] 未预期异常", e);
        return Result.<Void>fail("服务内部错误").code(500);
    }
}
