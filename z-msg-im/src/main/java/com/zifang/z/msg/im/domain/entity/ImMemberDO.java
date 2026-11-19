package com.zifang.z.msg.im.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

import java.time.LocalDateTime;

/**
 * IM 会话成员 DO
 * <p>
 * 未读数不在消息表上 count(*)，而是 {@code conversation.last_msg_seq - last_read_seq}：
 * 单聊/群聊里"读到哪"是每用户一条游标，用一行一列表达最省，且写放大只有一行。
 */
@BizModel(name = "IM 会话成员 DO", module = "z-msg", description = "IM 会话成员 DO 未读数不在消息表上 count(*)，而是 conversation.last_msg_seq - last_read_seq： 单聊/群聊里\"读到哪\"是每用户一条游标，用一行一列表达最省，且写放大只有一行。")
@TableName("z_msg_im_member")
public class ImMemberDO {

    @TableId(type = IdType.ASSIGN_ID)
    @BizField(name = "雪花 id", type = BizField.FieldType.INTEGER)
    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    private Long id;
    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    @BizField(name = "conversationId", type = BizField.FieldType.INTEGER)
    private Long conversationId;
    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    @BizField(name = "userId", type = BizField.FieldType.INTEGER)
    private Long userId;
    /**
     * OWNER / ADMIN / MEMBER
     */
    @BizField(name = "OWNER / ADMIN / MEMBER", type = BizField.FieldType.STRING)
    private String role;
    @BizField(name = "已读游标，只允许单调前进", type = BizField.FieldType.INTEGER)
    private Long lastReadSeq;
    /**
     * 1=免打扰：仍计未读，只是不弹
     */
    @BizField(name = "1=免打扰，仍计未读只是不弹", type = BizField.FieldType.INTEGER)
    private Integer muted;
    /**
     * 0=彻底关闭推送
     */
    @BizField(name = "0=完全关闭推送（连库都不查）", type = BizField.FieldType.INTEGER)
    private Integer pushSwitch;
    /**
     * "清空聊天记录"游标：只对本人生效，同步时作为 seq 下界，所以必须是 seq 而不是时间
     */
    @BizField(name = "清空聊天记录游标，同步时作为下界", type = BizField.FieldType.INTEGER)
    private Long clearedSeq;
    @BizField(name = "清空动作发生时间，供 UI 展示", type = BizField.FieldType.DATETIME)
    private LocalDateTime clearedTime;
    @BizField(name = "joinedTime", type = BizField.FieldType.DATETIME)
    private LocalDateTime joinedTime;
    @BizField(name = "updatedTime", type = BizField.FieldType.DATETIME)
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



