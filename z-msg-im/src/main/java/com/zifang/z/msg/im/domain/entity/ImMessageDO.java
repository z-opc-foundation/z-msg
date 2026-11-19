package com.zifang.z.msg.im.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

import java.time.LocalDateTime;

/**
 * IM 消息 DO
 * <p>
 * {@code (conversation_id, seq)} 与 {@code (conversation_id, client_msg_id)} 两个唯一索引
 * 分别兜住"seq 不重复"和"客户端重发不产生两条气泡"，是并发正确性的最后一道防线。
 */
@BizModel(name = "IM 消息 DO", module = "z-msg", description = "IM 消息 DO (conversation_id, seq) 与 (conversation_id, client_msg_id) 两个唯一索引 分别兜住\"seq 不重复\"和\"客户端重发不产生两条气泡\"，是并发正确性的最后一道防线。")
@TableName("z_msg_im_message")
public class ImMessageDO {

    @TableId(type = IdType.ASSIGN_ID)
    @BizField(name = "雪花 id", type = BizField.FieldType.INTEGER)
    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    private Long id;
    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    @BizField(name = "conversationId", type = BizField.FieldType.INTEGER)
    private Long conversationId;
    @BizField(name = "会话内单调递增，客户端按它做增量拉取与去重", type = BizField.FieldType.INTEGER)
    private Long seq;
    @BizField(name = "客户端本地 id，重发时命中唯一索引即视为幂等成功", type = BizField.FieldType.STRING)
    private String clientMsgId;
    // 雪花 id 出字符串：JS Number 只有 53 bit，理由见 ImMessageService#payloadOf
    @BizField(name = "senderUserId", type = BizField.FieldType.INTEGER)
    private Long senderUserId;
    /**
     * TEXT / IMAGE / FILE / AUDIO / SYS
     */
    @BizField(name = "TEXT / IMAGE / FILE / AUDIO / SYS", type = BizField.FieldType.STRING)
    private String msgType;
    @BizField(name = "content", type = BizField.FieldType.STRING)
    private String content;
    /**
     * 升序逗号分隔的 userId；用字符串而不是 JSON 列，是为了在 MySQL/H2 两边都能等值查询与索引
     */
    @BizField(name = "升序逗号分隔的 userId，避免 JSON 列的版本差异", type = BizField.FieldType.STRING)
    private String atUserIds;
    @BizField(name = "replyToSeq", type = BizField.FieldType.INTEGER)
    private Long replyToSeq;
    @BizField(name = "tenantCode", type = BizField.FieldType.STRING)
    private String tenantCode;
    @BizField(name = "createdTime", type = BizField.FieldType.DATETIME)
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



