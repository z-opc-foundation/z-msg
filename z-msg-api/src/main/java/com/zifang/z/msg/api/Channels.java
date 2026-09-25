package com.zifang.z.msg.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 统一渠道常量 (Phase 2-3)
 * <p>
 * 集中维护所有支持的渠道类型字符串,避免散落硬编码。
 * <p>
 * 注意：channel 描述"投递介质"，供应商是 provider（{@link ChannelSender#provider()}）。
 * 所以钉钉/企微/飞书是三个 IM channel，而"阿里云短信/腾讯云短信"同属 {@link #SMS}。
 */
public final class Channels {
    public static final String SMS = "SMS";
    public static final String EMAIL = "EMAIL";
    public static final String IN_APP = "IN_APP";
    public static final String WEBHOOK = "WEBHOOK";
    public static final String IM_WECOM = "IM_WECOM";
    public static final String IM_DINGTALK = "IM_DINGTALK";
    public static final String IM_FEISHU = "IM_FEISHU";
    /**
     * 微信公众号（模板消息 / 订阅通知）
     */
    public static final String IM_WEIXIN_MP = "IM_WEIXIN_MP";
    /**
     * 微信小程序（订阅消息）
     */
    public static final String IM_WEIXIN_MINI = "IM_WEIXIN_MINI";
    public static final String IM_SLACK = "IM_SLACK";
    public static final String PUSH_FCM = "PUSH_FCM";
    public static final String PUSH_APNS = "PUSH_APNS";
    public static final String PUSH_WEB = "PUSH_WEB";
    /**
     * 极光推送
     */
    public static final String PUSH_JPUSH = "PUSH_JPUSH";
    /**
     * 实时通道（WebSocket / SSE），由 z-msg-ws 提供；站内信落库后同时推到这里
     */
    public static final String REALTIME = "REALTIME";

    /**
     * 全部已知通道，用于配置校验与 /api/msg/channel/list 自省
     */
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            SMS, EMAIL, IN_APP, WEBHOOK, IM_WECOM, IM_DINGTALK, IM_FEISHU,
            IM_WEIXIN_MP, IM_WEIXIN_MINI, IM_SLACK,
            PUSH_FCM, PUSH_APNS, PUSH_WEB, PUSH_JPUSH, REALTIME));

    public static boolean isKnown(String channel) {
        if (channel == null) {
            return false;
        }
        for (String c : ALL) {
            if (c.equalsIgnoreCase(channel)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 归一化：trim + 大写，未知值原样返回（由调用决定是否拒绝）
     */
    public static String normalize(String channel) {
        return channel == null ? null : channel.trim().toUpperCase();
    }

    private Channels() {
    }
}
