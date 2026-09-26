package com.zifang.z.msg.im.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 成员角色常量（{@code z_msg_im_member.role}）。
 * <p>
 * 这里只有三种角色，而且只用来回答一个问题：<em>这个人在这个会话里能不能管理成员</em>。
 * 会话级的权限判定（订不订得到 {@code room:}）不看角色，只看成员表里有没有这一行 ——
 * 见 {@code ImTopicAuthorizationPolicy}：ADMIN 能被踢，OWNER 不能被踢，除此之外两者在
 * 实时链路上没有任何区别。刻意不做"自定义角色/权限位"，那是宿主自己的授权系统该干的事。
 */
public final class ImRoles {

    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String MEMBER = "MEMBER";

    private ImRoles() {
    }

    public static String normalize(String role) {
        if (role == null || role.trim().isEmpty()) {
            return MEMBER;
        }
        String r = role.trim().toUpperCase();
        if (!OWNER.equals(r) && !ADMIN.equals(r) && !MEMBER.equals(r)) {
            throw new IllegalArgumentException("未知的 role: " + role + "，取值 OWNER / ADMIN / MEMBER");
        }
        return r;
    }

    public static boolean isManager(String role) {
        return OWNER.equals(role) || ADMIN.equals(role);
    }

    /**
     * 会话列表/成员表要按"发起人一定在成员里"的形状入库，
     * 这里把 id 去重并保持入参顺序，避免调用方各写一份去重逻辑。
     */
    public static List<Long> distinct(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<Long>();
        }
        List<Long> out = new ArrayList<Long>(userIds.size());
        for (Long id : userIds) {
            if (id != null && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * 单聊稳定键：两个 userId 升序拼接。放在列上而不是靠查询去猜，
     * 是为了让"同一对用户只有一个会话"成为 {@code uk_im_conv_pair} 这条数据库约束。
     *
     * @return 形如 {@code 1001:1002}；任一方为空时返回 null（GROUP/ROOM 不走这个键）
     */
    public static String singlePairKey(Long a, Long b) {
        if (a == null || b == null) {
            return null;
        }
        long lo = Math.min(a.longValue(), b.longValue());
        long hi = Math.max(a.longValue(), b.longValue());
        return lo + ":" + hi;
    }

    /**
     * 单聊的对方是谁。非单聊、或者本人根本不在这一对用户里时返回 null。
     */
    public static Long peerOfSingle(String ukPair, Long me) {
        if (ukPair == null || me == null) {
            return null;
        }
        String[] parts = ukPair.split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            long lo = Long.parseLong(parts[0]);
            long hi = Long.parseLong(parts[1]);
            if (lo == me.longValue()) {
                return hi;
            }
            if (hi == me.longValue()) {
                return lo;
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * at 列表落库形态：升序逗号分隔，去重去空。
     * 用字符串而不是 JSON 列，是为了 MySQL/H2 两边都能等值查询与建索引。
     */
    public static String atUserIds(List<Long> userIds) {
        List<Long> ids = distinct(userIds);
        if (ids.isEmpty()) {
            return null;
        }
        Collections.sort(ids);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }
}
