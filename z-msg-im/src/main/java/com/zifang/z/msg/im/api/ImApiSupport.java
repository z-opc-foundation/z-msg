package com.zifang.z.msg.im.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 请求体取值的小工具。三个 controller 共用，避免每个字段各写一份
 * {@code instanceof Number} 判断——那种复制粘贴最容易漏掉"字符串形式的数字"这一档。
 * <p>
 * 约定：所有解析失败都抛 {@link IllegalArgumentException}，由 web 层的
 * {@code MsgWebExceptionHandler} 统一转成 400，不在 controller 里拼错误响应。
 */
final class ImApiSupport {

    private ImApiSupport() {
    }

    static Map<String, Object> requireBody(Map<String, Object> body) {
        if (body == null) {
            throw new IllegalArgumentException("请求体不能为空（JSON 对象）");
        }
        return body;
    }

    static Long id(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) {
            throw new IllegalArgumentException("缺少必填字段 " + key);
        }
        if (v instanceof Number) {
            return Long.valueOf(((Number) v).longValue());
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("字段 " + key + " 不能为空");
        }
        try {
            return Long.valueOf(Long.parseLong(s));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字段 " + key + " 需要数字 id，实际: " + v);
        }
    }

    static Long optionalId(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).trim().isEmpty()) {
            return null;
        }
        return id(body, key);
    }

    static long numberWithDefault(Map<String, Object> body, String key, long defaultValue) {
        Object v = body.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字段 " + key + " 需要整数，实际: " + v);
        }
    }

    static int intWithDefault(Map<String, Object> body, String key, int defaultValue) {
        long v = numberWithDefault(body, key, defaultValue);
        if (v < 0L || v > (long) Integer.MAX_VALUE) {
            throw new IllegalArgumentException("字段 " + key + " 超出范围: " + v);
        }
        return (int) v;
    }

    static String string(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    static boolean bool(Map<String, Object> body, String key, boolean defaultValue) {
        Object v = body.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        return Boolean.parseBoolean(String.valueOf(v).trim());
    }

    /**
     * userId 数组：接受 JSON 数组，也接受 {@code "1,2,3"} 这种前端偷懒写法。
     */
    @SuppressWarnings("unchecked")
    static List<Long> idList(Map<String, Object> body, String key) {
        Object v = body.get(key);
        List<Long> out = new ArrayList<Long>();
        if (v == null) {
            return out;
        }
        if (v instanceof List) {
            for (Object o : (List<Object>) v) {
                if (o == null) {
                    continue;
                }
                out.add(parseLong(key, o));
            }
            return out;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return out;
        }
        for (String part : s.split(",")) {
            if (!part.trim().isEmpty()) {
                out.add(parseLong(key, part.trim()));
            }
        }
        return out;
    }

    private static Long parseLong(String key, Object o) {
        if (o instanceof Number) {
            return Long.valueOf(((Number) o).longValue());
        }
        try {
            return Long.valueOf(Long.parseLong(String.valueOf(o).trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字段 " + key + " 里混进了非数字: " + o);
        }
    }
}
