package com.zifang.z.msg.im.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * IM 会话 DO
 * <p>
 * {@code last_msg_seq} 是会话内 seq 的唯一权威水位（见 {@code SeqAllocator}）：实时帧的 seq 也取它，
 * 所以在线帧序号与离线增量拉取的序号天然连续，客户端不需要维护两套游标。
 */
@TableName("z_msg_im_conversation")
public class ImConversationDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * SINGLE / GROUP / ROOM，取值见 {@link com.zifang.z.msg.im.domain.model.ImConvTypes}
     */
    private String convType;
    private String tenantCode;
    private String title;
    private String avatar;
    private Long ownerUserId;
    private Long lastMsgSeq;
    private Long lastMsgId;
    private String lastMsgPreview;
    private Integer memberCount;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    /**
     * 单聊稳定键：两个 userId 升序拼接（{@code min:max}）。放在行上而不是靠查询去猜，
     * 是为了让"同一对用户只允许一个会话"成为数据库约束而不是应用层的 if。
     */
    private String ukPair;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    public String getUkPair() {
        return ukPair;
    }

    public void setUkPair(String ukPair) {
        this.ukPair = ukPair;
    }
}
