package com.zifang.z.msg.im.domain.model;

/**
 * 会话类型常量（{@code z_msg_im_conversation.conv_type}）。
 * <p>
 * 三种类型的差别只在两件事上，代码里所有分支都由这两条推出：
 * <ul>
 *   <li>{@link #SINGLE} 必须占 {@code uk_pair}（同一对用户永远只有一个会话），
 *       且服务端会把消息额外投到对方的 {@code user:} topic；</li>
 *   <li>{@link #GROUP} / {@link #ROOM} 不占 {@code uk_pair}，成员可增删；
 *       GROUP 有人数上限并维护 {@code member_count}，ROOM 是"进来就能聊"的大房间，
 *       实时帧只走 {@code room:}，不做 {@code user:} 侧的逐人扇出。</li>
 * </ul>
 */
public final class ImConvTypes {

    public static final String SINGLE = "SINGLE";
    public static final String GROUP = "GROUP";
    public static final String ROOM = "ROOM";

    private ImConvTypes() {
    }

    public static boolean isKnown(String type) {
        return SINGLE.equals(type) || GROUP.equals(type) || ROOM.equals(type);
    }

    public static boolean isSingle(String type) {
        return SINGLE.equals(type);
    }

    /**
     * 归一化为大写；未知类型抛 {@link IllegalArgumentException}，
     * 由 web 层统一映射成 400，而不是让脏数据进库后再靠查询猜。
     */
    public static String normalize(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("convType 不能为空，取值 SINGLE / GROUP / ROOM");
        }
        String t = type.trim().toUpperCase();
        if (!isKnown(t)) {
            throw new IllegalArgumentException("未知的 convType: " + type + "，取值 SINGLE / GROUP / ROOM");
        }
        return t;
    }
}
