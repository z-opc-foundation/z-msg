package com.zifang.z.msg.core.json;

import com.zifang.util.json.JsonUtil;

/**
 * z-msg 统一 JSON 工具 (1.1.0)
 * <p>
 * 实时帧的 payload、webhook body、IM 消息体都要序列化；这里用 z-util 的 JsonUtil
 * 做宽松 + 精简的序列化：反序列化本就忽略未知字段，序列化统一走 toJsonExcludingNulls，
 * 避免对端多一个字段就把整条链路打挂。
 * <p>
 * LocalDateTime 之类的时间字段由 z-util-json 自己的序列化器处理，识别不到时退化为
 * {@code toString()} 字符串，不至于抛异常。
 */
public final class MsgJson {

    private MsgJson() {
    }

    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return JsonUtil.toJsonExcludingNulls(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("z-msg 序列化失败: " + e.getMessage(), e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return JsonUtil.fromJson(json, type);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("z-msg 反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析为 Map，非对象 JSON 返回 null（对端 payload 容错用）
     */
    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> toMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            Object plain = JsonUtil.toPlainValue(JsonUtil.parse(json));
            if (!(plain instanceof java.util.Map)) {
                return null;
            }
            return (java.util.Map<String, Object>) plain;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 判断字符串是否可当 JSON 对象解析（用于 payload 是裸串还是结构体的分支）
     */
    public static boolean looksLikeJsonObject(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        return t.startsWith("{") && t.endsWith("}");
    }
}
