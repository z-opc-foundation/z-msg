package com.zifang.z.msg.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * web 层开关。默认值一律取"关"：这些端点一旦不带身份就能用，代价是别人的收件箱和通知设置。
 */
@ConfigurationProperties(prefix = "z-msg.web")
public class MsgWebProperties {

    /**
     * 是否认 {@code X-Msg-User-Id} 这类可信头。打开前必须确认上游网关会覆盖/剥离客户端自带的同名头。
     */
    private boolean trustedHeaderEnabled = false;
    private String trustedHeaderName = "X-Msg-User-Id";

    /**
     * 服务间用的事件投递端点。默认关，见 {@code MessageController#publish}。
     */
    private boolean publishEndpointEnabled = false;

    public boolean isTrustedHeaderEnabled() {
        return trustedHeaderEnabled;
    }

    public void setTrustedHeaderEnabled(boolean v) {
        this.trustedHeaderEnabled = v;
    }

    public String getTrustedHeaderName() {
        return trustedHeaderName;
    }

    public void setTrustedHeaderName(String v) {
        this.trustedHeaderName = v;
    }

    public boolean isPublishEndpointEnabled() {
        return publishEndpointEnabled;
    }

    public void setPublishEndpointEnabled(boolean v) {
        this.publishEndpointEnabled = v;
    }
}
