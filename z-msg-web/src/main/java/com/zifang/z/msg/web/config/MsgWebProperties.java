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

    /**
     * 服务间用的直发端点（{@code POST /api/msg/send}），默认关，见 {@code MsgSendController}。
     * <p>
     * 与 {@link #publishEndpointEnabled} 同款姿势：默认关、宿主确认调用方在网络边界内之后才打开。
     * 两者管的是同一类"能往任意收件人发消息"的服务间入口，区别只是 publish 收的是
     * 业务事件（模板渲染 + 站内信兜底），send 收的是运行时全文（直填渠道 + 收件人）。
     */
    private boolean sendEndpointEnabled = false;

    /**
     * 管理面总开关（模板 / 批量任务 / 投递日志），默认关，判定在 {@link AdminEndpointGate}。
     * <p>
     * 这三组端点不带任何身份：template 的 8 个映射能增删改并审批模板，batch 的 4 个能提交群发任务，
     * delivery 的 3 条路径能按客户端自报的 userId 翻别人的投递记录和成功率。
     * 宿主确认调用方都在认证边界内之后再用 {@code z-msg.web.admin-endpoints-enabled=true} 打开。
     */
    private boolean adminEndpointsEnabled = false;

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

    public boolean isAdminEndpointsEnabled() {
        return adminEndpointsEnabled;
    }

    public void setAdminEndpointsEnabled(boolean v) {
        this.adminEndpointsEnabled = v;
    }

    public boolean isSendEndpointEnabled() {
        return sendEndpointEnabled;
    }

    public void setSendEndpointEnabled(boolean v) {
        this.sendEndpointEnabled = v;
    }
}
