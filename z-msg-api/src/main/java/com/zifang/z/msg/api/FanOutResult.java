package com.zifang.z.msg.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 多渠道 fan-out 结果 (1.1.0)
 * <p>
 * 一次业务事件按通道逐个投递，每个通道产出一条 {@link Outcome}。
 * 之所以不返回单个 {@link MessageSendResult}：部分通道失败（短信被限流）时业务通常
 * 仍要接受"邮件已送达"这个事实，笼统的 success 会把可用的降级也报成失败。
 */
public class FanOutResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;
    /**
     * mock provider 产生的"未真正外发"记录，不计入成功率
     */
    public static final int STATUS_MOCK = 3;
    /**
     * 被偏好/静默时段/未配置 provider 挡下，未产生外发调用
     */
    public static final int STATUS_SKIPPED = 4;

    private final List<Outcome> outcomes = new ArrayList<>();
    private String bizType;
    private Long userId;

    public FanOutResult() {
    }

    public FanOutResult(String bizType, Long userId) {
        this.bizType = bizType;
        this.userId = userId;
    }

    public void add(Outcome o) {
        if (o != null) {
            outcomes.add(o);
        }
    }

    public List<Outcome> getOutcomes() {
        return outcomes;
    }

    public void setOutcomes(List<Outcome> v) {
        outcomes.clear();
        if (v != null) {
            outcomes.addAll(v);
        }
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public boolean isEmpty() {
        return outcomes.isEmpty();
    }

    /**
     * 是否至少一个真实通道投递成功（mock 不算）
     */
    public boolean anyDelivered() {
        for (Outcome o : outcomes) {
            if (o.getStatus() == STATUS_SUCCESS) {
                return true;
            }
        }
        return false;
    }

    public boolean allSucceeded() {
        return !outcomes.isEmpty() && anyDelivered() && !anyFailed();
    }

    public boolean anyFailed() {
        for (Outcome o : outcomes) {
            if (o.getStatus() == STATUS_FAILED) {
                return true;
            }
        }
        return false;
    }

    public int countStatus(int status) {
        int n = 0;
        for (Outcome o : outcomes) {
            if (o.getStatus() == status) {
                n++;
            }
        }
        return n;
    }

    public List<String> channelsWith(int status) {
        List<String> list = new ArrayList<>();
        for (Outcome o : outcomes) {
            if (o.getStatus() == status) {
                list.add(o.getChannel());
            }
        }
        return list;
    }

    public Outcome outcome(String channel) {
        for (Outcome o : outcomes) {
            if (o.getChannel() != null && o.getChannel().equalsIgnoreCase(channel)) {
                return o;
            }
        }
        return null;
    }

    /**
     * 供测试与序列化使用的只读视图
     */
    public List<Outcome> unmodifiable() {
        return Collections.unmodifiableList(outcomes);
    }

    @Override
    public String toString() {
        return "FanOutResult{bizType='" + bizType + "', userId=" + userId + ", outcomes=" + outcomes + '}';
    }

    public static class Outcome implements Serializable {
        private static final long serialVersionUID = 1L;

        private String channel;
        private String provider;
        private String receiver;
        private int status;
        private String errorCode;
        private String errorMessage;
        private String providerMessageId;
        private int attempts;
        private long durationMs;
        /**
         * 实际命中的模板行 id（跳过渲染时为 null）
         */
        private Long templateId;

        public Outcome() {
        }

        public Outcome(String channel, int status, String errorCode, String errorMessage) {
            this.channel = channel;
            this.status = status;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static Outcome success(String channel, String provider, String providerMessageId) {
            Outcome o = new Outcome();
            o.channel = channel;
            o.provider = provider;
            o.status = STATUS_SUCCESS;
            o.providerMessageId = providerMessageId;
            return o;
        }

        public static Outcome failed(String channel, String provider, String errorCode, String errorMessage) {
            Outcome o = new Outcome();
            o.channel = channel;
            o.provider = provider;
            o.status = STATUS_FAILED;
            o.errorCode = errorCode;
            o.errorMessage = errorMessage;
            return o;
        }

        public static Outcome skipped(String channel, String reasonCode, String reason) {
            return new Outcome(channel, STATUS_SKIPPED, reasonCode, reason);
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getReceiver() {
            return receiver;
        }

        public void setReceiver(String receiver) {
            this.receiver = receiver;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public boolean isSuccess() {
            return status == STATUS_SUCCESS;
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

        public String getProviderMessageId() {
            return providerMessageId;
        }

        public void setProviderMessageId(String providerMessageId) {
            this.providerMessageId = providerMessageId;
        }

        public int getAttempts() {
            return attempts;
        }

        public void setAttempts(int attempts) {
            this.attempts = attempts;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }

        public Long getTemplateId() {
            return templateId;
        }

        public void setTemplateId(Long templateId) {
            this.templateId = templateId;
        }

        @Override
        public String toString() {
            return "{channel=" + channel + ", provider=" + provider + ", status=" + status
                    + (errorCode == null ? "" : ", err=" + errorCode) + ", attempts=" + attempts
                    + ", ms=" + durationMs + '}';
        }
    }
}
