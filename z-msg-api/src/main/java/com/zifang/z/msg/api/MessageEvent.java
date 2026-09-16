package com.zifang.z.msg.api;

import java.time.LocalDateTime;
import java.util.Map;

public class MessageEvent {
    private String eventType;
    private Long userId;
    private Map<String, Object> params;
    private LocalDateTime occurredAt;
    /**
     * 幂等键 (Phase 3: 同一 bizType+userId+dedupKey 不重复入库)
     */
    private String dedupKey;
    /**
     * 消息跳转链接 (Phase 3: 点击消息跳转到指定 URL)
     */
    private String linkUrl;

    public MessageEvent() {
    }

    public MessageEvent(String eventType, Long userId, Map<String, Object> params) {
        this.eventType = eventType;
        this.userId = userId;
        this.params = params;
        this.occurredAt = LocalDateTime.now();
    }

    /**
     * 全参构造 (Phase 3: 支持 dedupKey + linkUrl)
     */
    public MessageEvent(String eventType, Long userId, Map<String, Object> params,
                        String dedupKey, String linkUrl) {
        this(eventType, userId, params);
        this.dedupKey = dedupKey;
        this.linkUrl = linkUrl;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }
}
