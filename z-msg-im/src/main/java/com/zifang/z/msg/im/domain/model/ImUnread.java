package com.zifang.z.msg.im.domain.model;

/**
 * 未读汇总的一行（一个会话一条），以及未读口径的唯一计算处 {@link #of}。
 * <p>
 * 口径三条，全部在这里落地，别处不许再各写一份减法：
 * <ol>
 *   <li>未读 = {@code conversation.last_msg_seq - 有效游标}，其中有效游标取
 *       {@code last_read_seq} 与 {@code cleared_seq} 的较大者 —— 清空过的会话
 *       不该把清掉的那段又算成未读；</li>
 *   <li>下限 0：水位被 CAS 抬走、而游标已经跑到前面（例如另一个客户端标了更高的 seq）
 *       不能给出负数；</li>
 *   <li>不 count 消息表：一行一列的游标差就是未读，写放大只有一行。</li>
 * </ol>
 */
public class ImUnread {

    private Long conversationId;
    private String convType;
    private String title;
    private long lastMsgSeq;
    private long lastReadSeq;
    private long clearedSeq;
    private long unreadCount;
    private int muted;

    public static long of(Long lastMsgSeq, Long lastReadSeq, Long clearedSeq) {
        long head = lastMsgSeq == null ? 0L : lastMsgSeq.longValue();
        long read = lastReadSeq == null ? 0L : lastReadSeq.longValue();
        long cleared = clearedSeq == null ? 0L : clearedSeq.longValue();
        long cursor = Math.max(read, cleared);
        long unread = head - cursor;
        return unread < 0L ? 0L : unread;
    }

    public static ImUnread of(Long conversationId, String convType, String title,
                              Long lastMsgSeq, Long lastReadSeq, Long clearedSeq, Integer muted) {
        ImUnread u = new ImUnread();
        u.conversationId = conversationId;
        u.convType = convType;
        u.title = title;
        u.lastMsgSeq = lastMsgSeq == null ? 0L : lastMsgSeq.longValue();
        u.lastReadSeq = lastReadSeq == null ? 0L : lastReadSeq.longValue();
        u.clearedSeq = clearedSeq == null ? 0L : clearedSeq.longValue();
        u.muted = muted == null ? 0 : muted.intValue();
        u.unreadCount = of(lastMsgSeq, lastReadSeq, clearedSeq);
        return u;
    }

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getLastMsgSeq() {
        return lastMsgSeq;
    }

    public void setLastMsgSeq(long lastMsgSeq) {
        this.lastMsgSeq = lastMsgSeq;
    }

    public long getLastReadSeq() {
        return lastReadSeq;
    }

    public void setLastReadSeq(long lastReadSeq) {
        this.lastReadSeq = lastReadSeq;
    }

    public long getClearedSeq() {
        return clearedSeq;
    }

    public void setClearedSeq(long clearedSeq) {
        this.clearedSeq = clearedSeq;
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(long unreadCount) {
        this.unreadCount = unreadCount;
    }

    public int getMuted() {
        return muted;
    }

    public void setMuted(int muted) {
        this.muted = muted;
    }
}
