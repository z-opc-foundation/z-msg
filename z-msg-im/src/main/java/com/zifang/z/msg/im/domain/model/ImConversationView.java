package com.zifang.z.msg.im.domain.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 会话列表的一行：会话上的展示字段 + 调用者自己的游标 + 算出来的未读数。
 * <p>
 * 这是 {@code ImConversationMapper#selectMinePage} 那条 join 的投影，字段名即列别名，
 * 所以改名要连着 SQL 一起改。
 * <p>
 * 刻意不含 {@code content} 之类的大字段：会话列表要能一次拉几十条。
 */
public class ImConversationView {

    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;
    private String convType;
    private String tenantCode;
    private String title;
    private String avatar;
    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerUserId;
    private Long lastMsgSeq;
    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    @JsonSerialize(using = ToStringSerializer.class)
    private Long lastMsgId;
    private String lastMsgPreview;
    private Integer memberCount;
    private LocalDateTime updatedTime;
    private LocalDateTime createdTime;

    /** 调用者在这个会话里的角色 OWNER / ADMIN / MEMBER */
    private String myRole;
    private Long myLastReadSeq;
    private Long myClearedSeq;
    private Integer muted;
    /** 服务层按 {@code lastMsgSeq - max(myLastReadSeq, myClearedSeq)} 算好后填进来 */
    private long unreadCount;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getConvType() {
        return convType;
    }

    public void setConvType(String convType) {
        this.convType = convType;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getLastMsgSeq() {
        return lastMsgSeq;
    }

    public void setLastMsgSeq(Long lastMsgSeq) {
        this.lastMsgSeq = lastMsgSeq;
    }

    public Long getLastMsgId() {
        return lastMsgId;
    }

    public void setLastMsgId(Long lastMsgId) {
        this.lastMsgId = lastMsgId;
    }

    public String getLastMsgPreview() {
        return lastMsgPreview;
    }

    public void setLastMsgPreview(String lastMsgPreview) {
        this.lastMsgPreview = lastMsgPreview;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public String getMyRole() {
        return myRole;
    }

    public void setMyRole(String myRole) {
        this.myRole = myRole;
    }

    public Long getMyLastReadSeq() {
        return myLastReadSeq;
    }

    public void setMyLastReadSeq(Long myLastReadSeq) {
        this.myLastReadSeq = myLastReadSeq;
    }

    public Long getMyClearedSeq() {
        return myClearedSeq;
    }

    public void setMyClearedSeq(Long myClearedSeq) {
        this.myClearedSeq = myClearedSeq;
    }

    public Integer getMuted() {
        return muted;
    }

    public void setMuted(Integer muted) {
        this.muted = muted;
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(long unreadCount) {
        this.unreadCount = unreadCount;
    }
}
