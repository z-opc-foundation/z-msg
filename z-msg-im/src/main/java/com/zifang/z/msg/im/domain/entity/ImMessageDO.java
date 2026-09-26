package com.zifang.z.msg.im.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * IM 消息 DO
 * <p>
 * {@code (conversation_id, seq)} 与 {@code (conversation_id, client_msg_id)} 两个唯一索引
 * 分别兜住"seq 不重复"和"客户端重发不产生两条气泡"，是并发正确性的最后一道防线。
 */
@TableName("z_msg_im_message")
public class ImMessageDO {

    @TableId(type = IdType.ASSIGN_ID)
    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;
    private Long seq;
    private String clientMsgId;
    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderUserId;
    /**
     * TEXT / IMAGE / FILE / AUDIO / SYS
     */
    private String msgType;
    private String content;
    /**
     * 升序逗号分隔的 userId；用字符串而不是 JSON 列，是为了在 MySQL/H2 两边都能等值查询与索引
     */
    private String atUserIds;
    private Long replyToSeq;
    private String tenantCode;
    private LocalDateTime createdTime;

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

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(Long senderUserId) {
        this.senderUserId = senderUserId;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAtUserIds() {
        return atUserIds;
    }

    public void setAtUserIds(String atUserIds) {
        this.atUserIds = atUserIds;
    }

    public Long getReplyToSeq() {
        return replyToSeq;
    }

    public void setReplyToSeq(Long replyToSeq) {
        this.replyToSeq = replyToSeq;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }
}
