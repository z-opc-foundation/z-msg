package com.zifang.z.msg.api;

/**
 * 统一渠道常量 (Phase 2-3)
 * <p>
 * 集中维护所有支持的渠道类型字符串,避免散落硬编码。
 */
public final class Channels {
    public static final String SMS = "SMS";
    public static final String EMAIL = "EMAIL";
    public static final String IN_APP = "IN_APP";
    public static final String WEBHOOK = "WEBHOOK";
    public static final String IM_WECOM = "IM_WECOM";
    public static final String IM_DINGTALK = "IM_DINGTALK";
    public static final String IM_FEISHU = "IM_FEISHU";
    public static final String PUSH_FCM = "PUSH_FCM";
    public static final String PUSH_APNS = "PUSH_APNS";
    public static final String PUSH_WEB = "PUSH_WEB";

    private Channels() {
    }
}
