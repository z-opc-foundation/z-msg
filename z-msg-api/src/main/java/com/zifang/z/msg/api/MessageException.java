package com.zifang.z.msg.api;

/**
 * 消息下发业务异常
 * 区别于网络/系统异常：表示业务侧已知失败（如余额不足、模板不存在、签名错）
 */
public class MessageException extends RuntimeException {

    private final String errorCode;

    public MessageException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MessageException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
