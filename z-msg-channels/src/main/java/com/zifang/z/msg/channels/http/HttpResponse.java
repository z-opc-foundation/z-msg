package com.zifang.z.msg.channels.http;

/**
 * 一次 HTTP 交换的结果。永不抛异常：传输层错误落在
 * {@link #getErrorCode()} / {@link #getErrorMessage()} 上，由 provider 翻成
 * {@code MessageSendResult.fail}。
 */
public class HttpResponse {

    private final int status;
    private final String body;
    private final String errorCode;
    private final String errorMessage;

    private HttpResponse(int status, String body, String errorCode, String errorMessage) {
        this.status = status;
        this.body = body;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static HttpResponse of(int status, String body) {
        return new HttpResponse(status, body, null, null);
    }

    public static HttpResponse error(String errorCode, String errorMessage) {
        return new HttpResponse(-1, null, errorCode, errorMessage);
    }

    /** 2xx */
    public boolean is2xx() {
        return status >= 200 && status < 300;
    }

    /** 传输层失败（连接被拒/超时/协议错误），errorCode 非空 */
    public boolean isTransportError() {
        return errorCode != null;
    }

    /** 值得在 HTTP 层重试的：传输错误或 5xx（供应商业务拒绝是 2xx+错误码，不在此列） */
    public boolean isRetryable() {
        return isTransportError() || status >= 500;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return errorCode != null ? "HttpResponse{error=" + errorCode + "}" : "HttpResponse{status=" + status + "}";
    }
}
