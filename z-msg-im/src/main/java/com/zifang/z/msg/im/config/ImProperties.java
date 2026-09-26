package com.zifang.z.msg.im.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code z-msg-im} 配置（前缀 {@code z-msg.im}）。
 * <p>
 * 判据和 {@code WsProperties} 一样：写在配置里但没人读的字段就是骗人的广告。
 * 每个字段的"谁在读"都标在注释上，README 的配置表照抄这里。
 */
@ConfigurationProperties(prefix = "z-msg.im")
public class ImProperties {

    /**
     * 关掉后本模块一个 bean 都不注册（连 mapper 扫描一起退避）。
     * 读处：{@link MsgImAutoConfiguration} 的 {@code @ConditionalOnProperty}。
     */
    private boolean enabled = true;

    /**
     * 会话列表 / 历史消息不传 size 时用这个值。
     * 读处：{@code ImConversationService#pageMine}、{@code ImMessageService#historyPage}。
     */
    private int defaultPageSize = 50;

    /**
     * size 的硬上限，防止一个 {@code size=100000} 把整张消息表拖进内存。
     * 读处：同上两个方法的 clampSize。
     */
    private int maxPageSize = 200;

    /**
     * GROUP 的成员上限（{@code ROOM} 不受这个数限制，它走 {@code room:} 一个 topic 扇出）。
     * 读处：{@code ImConversationService#createGroup} 与 {@code addMembers}。
     */
    private int maxMembersPerConversation = 500;

    /**
     * 单条正文长度上限，超了直接 400，不做静默截断（截断会让客户端显示的和自己发的不一样）。
     * 读处：{@code ImMessageService#send}。
     */
    private int maxContentLength = 4000;

    /**
     * 占号 CAS 的最大尝试次数；用尽即抛，宁可让调用方看到失败，也不允许在同一会话里
     * 交出两个相同的 seq。读处：{@code ImSequencer#next}。
     */
    private int seqCasMaxAttempts = 200;

    /**
     * CAS 失败后的让步毫秒数，0 = 不让步。默认 1ms：一个会话的写入是"人打字"级别的频率，
     * 让步换 CPU 划得来。读处：{@code ImSequencer#next}。
     */
    private long seqCasBackoffMs = 1L;

    /**
     * 总开关：false 时 IM 只落库，一个实时帧都不发（{@code room:} 与 {@code user:} 两侧都不发）。
     * 读处：{@code ImMessageService#publishRoom} / {@code ImReadService#markRead}。
     */
    private boolean publishRealtime = true;

    /**
     * 标已读时是否往 {@code room:} 投一条 {@code kind=read} 帧（群里"谁读到哪"的实时展示）。
     * 读处：{@code ImReadService#markRead}。
     */
    private boolean publishReadReceipt = true;

    /**
     * 是否额外把消息投到成员的 {@code user:<id>} topic。
     * 单聊靠它做到"客户端只订自己收件箱也能收到 DM"；群聊只在人数不超过
     * {@link #userSidePushMaxMembers} 时才扇出。读处：{@code ImMessageService#publishToMembers}。
     */
    private boolean userSidePush = true;

    /**
     * {@code user:} 侧逐人扇出的人数闸：超过就不扇，避免大群里一条消息打出 N 次投递。
     * 读处：同上。
     */
    private int userSidePushMaxMembers = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getMaxMembersPerConversation() {
        return maxMembersPerConversation;
    }

    public void setMaxMembersPerConversation(int maxMembersPerConversation) {
        this.maxMembersPerConversation = maxMembersPerConversation;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public int getSeqCasMaxAttempts() {
        return seqCasMaxAttempts;
    }

    public void setSeqCasMaxAttempts(int seqCasMaxAttempts) {
        this.seqCasMaxAttempts = seqCasMaxAttempts;
    }

    public long getSeqCasBackoffMs() {
        return seqCasBackoffMs;
    }

    public void setSeqCasBackoffMs(long seqCasBackoffMs) {
        this.seqCasBackoffMs = seqCasBackoffMs;
    }

    public boolean isPublishRealtime() {
        return publishRealtime;
    }

    public void setPublishRealtime(boolean publishRealtime) {
        this.publishRealtime = publishRealtime;
    }

    public boolean isPublishReadReceipt() {
        return publishReadReceipt;
    }

    public void setPublishReadReceipt(boolean publishReadReceipt) {
        this.publishReadReceipt = publishReadReceipt;
    }

    public boolean isUserSidePush() {
        return userSidePush;
    }

    public void setUserSidePush(boolean userSidePush) {
        this.userSidePush = userSidePush;
    }

    public int getUserSidePushMaxMembers() {
        return userSidePushMaxMembers;
    }

    public void setUserSidePushMaxMembers(int userSidePushMaxMembers) {
        this.userSidePushMaxMembers = userSidePushMaxMembers;
    }
}
