package com.zifang.z.msg.client;

import java.util.Collections;
import java.util.Map;

/**
 * z-msg 客户端异常。消费方只需要处理这一个形状。
 */
public class ZMsgClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** HTTP 状态码；网络层失败时为 0。 */
    private final int statusCode;
    /** 服务端回的业务码（{@code Result.code}）；拿不到时为 0。 */
    private final int bizCode;

    public ZMsgClientException(String message, int statusCode, int bizCode) {
        super(message);
        this.statusCode = statusCode;
        this.bizCode = bizCode;
    }

    public ZMsgClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.bizCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getBizCode() {
        return bizCode;
    }

    /** 便捷构造：把服务端回的信封拼成一句人话。 */
    static ZMsgClientException of(String prefix, int statusCode, Map<String, Object> envelope) {
        Object message = envelope == null ? null : envelope.get("message");
        Object code = envelope == null ? null : envelope.get("code");
        int bizCode = code instanceof Number ? ((Number) code).intValue() : 0;
        String text = message == null ? "" : String.valueOf(message);
        return new ZMsgClientException(prefix + "：" + text, statusCode, bizCode);
    }

    static Map<String, Object> emptyEnvelope() {
        return Collections.emptyMap();
    }
}
