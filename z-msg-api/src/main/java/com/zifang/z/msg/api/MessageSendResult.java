package com.zifang.z.msg.api;

import java.io.Serializable;

public class MessageSendResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private String providerMessageId;
    private String errorCode;
    private String errorMessage;
    private String provider;

    public MessageSendResult() {
    }

    public MessageSendResult(boolean success, String providerMessageId, String errorCode, String errorMessage, String provider) {
        this.success = success;
        this.providerMessageId = providerMessageId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.provider = provider;
    }

    public static MessageSendResult ok(String provider, String providerMessageId) {
        return new MessageSendResult(true, providerMessageId, null, null, provider);
    }

    public static MessageSendResult fail(String provider, String errorCode, String errorMessage) {
        return new MessageSendResult(false, null, errorCode, errorMessage, provider);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
