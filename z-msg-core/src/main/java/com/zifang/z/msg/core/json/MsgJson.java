package com.zifang.z.msg.core.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * z-msg 统一 JSON 工具 (1.1.0)
 * <p>
 * 实时帧的 payload、webhook body、IM 消息体都要序列化；直接用 new ObjectMapper()
 * 会带上 Boot 默认策略（FAIL_ON_UNKNOWN_PROPERTIES=true + 输出全部 null 字段），
 * 对端多一个字段就把整条链路打挂，所以这里固定成宽松 + 精简的专用实例。
 * <p>
 * LocalDateTime 之类的时间字段依赖 jackson-datatype-jsr310；它由宿主（z-boot-web-starter）
 * 带入，本模块不硬依赖，因此用 findAndRegisterModules 探测式注册——没有时退化为
 * {@code toString()} 字符串，不至于抛 InvalidDefinitionException。
 */
public final class MsgJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .findAndRegisterModules();

    private MsgJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("z-msg 序列化失败: " + e.getOriginalMessage(), e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
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
            return MAPPER.readValue(json, java.util.Map.class);
        } catch (Exception e) {
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
