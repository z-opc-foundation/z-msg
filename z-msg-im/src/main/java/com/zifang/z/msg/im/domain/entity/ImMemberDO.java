package com.zifang.z.msg.im.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * IM 会话成员 DO
 * <p>
 * 未读数不在消息表上 count(*)，而是 {@code conversation.last_msg_seq - last_read_seq}：
 * 单聊/群聊里"读到哪"是每用户一条游标，用一行一列表达最省，且写放大只有一行。
 */
@TableName("z_msg_im_member")
public class ImMemberDO {

    @TableId(type = IdType.ASSIGN_ID)
    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;
    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    /**
     * OWNER / ADMIN / MEMBER
     */
    private String role;
    private Long lastReadSeq;
    /**
     * 1=免打扰：仍计未读，只是不弹
     */
    private Integer muted;
    /**
     * 0=彻底关闭推送
     */
    private Integer pushSwitch;
    /**
     * "清空聊天记录"游标：只对本人生效，同步时作为 seq 下界，所以必须是 seq 而不是时间
     */
    private Long clearedSeq;
    private LocalDateTime clearedTime;
    private LocalDateTime joinedTime;
    private LocalDateTime updatedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getLastReadSeq() {
        return lastReadSeq;
    }

    public void setLastReadSeq(Long lastReadSeq) {
        this.lastReadSeq = lastReadSeq;
    }

    public Integer getMuted() {
        return muted;
    }

    public void setMuted(Integer muted) {
        this.muted = muted;
    }

    public Integer getPushSwitch() {
        return pushSwitch;
    }

    public void setPushSwitch(Integer pushSwitch) {
        this.pushSwitch = pushSwitch;
    }

    public Long getClearedSeq() {
        return clearedSeq;
    }

    public void setClearedSeq(Long clearedSeq) {
        this.clearedSeq = clearedSeq;
    }

    public LocalDateTime getClearedTime() {
        return clearedTime;
    }

    public void setClearedTime(LocalDateTime clearedTime) {
        this.clearedTime = clearedTime;
    }

    public LocalDateTime getJoinedTime() {
        return joinedTime;
    }

    public void setJoinedTime(LocalDateTime joinedTime) {
        this.joinedTime = joinedTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }
}
