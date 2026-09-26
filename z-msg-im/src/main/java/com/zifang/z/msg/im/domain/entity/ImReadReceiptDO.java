package com.zifang.z.msg.im.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * IM 已读回执 DO（{@code z_msg_im_read_receipt}）
 * <p>
 * 与 {@link ImMemberDO#getLastReadSeq()} 是同一件事的两个视角：成员行上的游标是
 * "我自己读到哪"（未读数由它算，写路径只碰一行），这张表是"给群里别人看的展示态"
 * ——谁读到哪。之所以单独一张表而不是从成员表查：成员行会随退群删掉，
 * 而回执是已经发生过的事实，不能因为人走了就把"他读过"这段历史一起消失。
 * <p>
 * {@code (conversation_id, user_id)} 上的 {@code uk_im_receipt_conv_user} 是幂等的来源：
 * 标已读是高频重复动作，这里一行一人，靠 upsert 而不是 insert。
 */
@TableName("z_msg_im_read_receipt")
public class ImReadReceiptDO {

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
     * 只允许单调前进，写 SQL 上带 {@code last_read_seq < #{seq}} 条件，
     * 乱序到达的旧值改不动新值（返回 0 行而不是把水位回退）
     */
    private Long lastReadSeq;
    private LocalDateTime createdTime;
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

    public Long getLastReadSeq() {
        return lastReadSeq;
    }

    public void setLastReadSeq(Long lastReadSeq) {
        this.lastReadSeq = lastReadSeq;
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
}
