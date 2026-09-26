package com.zifang.z.msg.api;

/**
 * 实时 topic 命名规范 (1.1.0)
 * <p>
 * 一条连接可订阅多个 topic；服务端在握手阶段按登录用户自动订阅 {@link #user} 与
 * {@link #tenant} 前缀，业务侧只需订阅房间/会话。
 * <p>
 * 命名一律 {@code 前缀:标识}，解析按第一个冒号切分。除 {@link #P_BIZ} 天生带一段分组
 * （{@code biz:<group>:<key>}，此时 {@link #keyOf} 带回整段 {@code group:key}）之外，
 * 标识内不允许再出现冒号。
 * <p>
 * {@code room:} 的标识是会话 id：它在线上是 19 位雪花，<b>拼 topic 请用字符串而不是
 * Number</b>（JS 的 Number 只有 53 bit，会舍掉末位），见 {@code _doc/001_WS_PROTOCOL.md} §2。
 */
public final class RealtimeTopics {

    /**
     * 个人收件箱：{@code user:1001} —— 站内信、系统通知实时推到这里
     */
    public static final String P_USER = "user:";
    /**
     * IM 会话/房间：{@code room:<conversationId>}
     */
    public static final String P_ROOM = "room:";
    /**
     * 租户广播：{@code tenant:<tenantCode>} —— 管理员全员通知
     */
    public static final String P_TENANT = "tenant:";
    /**
     * 全域广播：{@code sys:broadcast} —— 不区分租户的公告
     */
    public static final String SYS_BROADCAST = "sys:broadcast";
    /**
     * 业务自定义事件：{@code biz:<group>:<key>}
     */
    public static final String P_BIZ = "biz:";

    public static final String SEP = ":";

    private RealtimeTopics() {
    }

    public static String user(Object userId) {
        return P_USER + userId;
    }

    public static String room(Object conversationId) {
        return P_ROOM + conversationId;
    }

    public static String tenant(String tenantCode) {
        return P_TENANT + tenantCode;
    }

    public static String biz(String group, String key) {
        return P_BIZ + group + SEP + key;
    }

    public static boolean isUser(String topic) {
        return topic != null && topic.startsWith(P_USER);
    }

    public static boolean isRoom(String topic) {
        return topic != null && topic.startsWith(P_ROOM);
    }

    public static boolean isTenant(String topic) {
        return topic != null && topic.startsWith(P_TENANT);
    }

    /**
     * topic 前缀，无冒号时返回整个 topic
     */
    public static String prefixOf(String topic) {
        if (topic == null || topic.isEmpty()) {
            return "";
        }
        int i = topic.indexOf(SEP);
        return i < 0 ? topic : topic.substring(0, i);
    }

    /**
     * topic 标识部分（第一个冒号之后），无冒号返回空串
     */
    public static String keyOf(String topic) {
        if (topic == null) {
            return "";
        }
        int i = topic.indexOf(SEP);
        return i < 0 ? "" : topic.substring(i + 1);
    }

    public static long userIdOf(String topic) {
        String k = keyOf(topic);
        try {
            return k.isEmpty() ? -1L : Long.parseLong(k);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
