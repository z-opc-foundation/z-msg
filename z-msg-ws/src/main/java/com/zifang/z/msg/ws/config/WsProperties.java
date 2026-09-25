package com.zifang.z.msg.ws.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code z-msg-ws} 接入层配置。
 * <p>
 * 每个字段都必须有真实的消费方，写在这里但没人读的配置就是骗人的广告
 * （1.0.x 的 {@code z-msg.email.smtp-*} 四项就是这个情况）。
 */
@ConfigurationProperties(prefix = "z-msg.ws")
public class WsProperties {

    /**
     * 关掉后本模块一个 bean 都不注册；站内信只落库不实时推，功能不受影响
     */
    private boolean enabled = true;

    /**
     * 端点路径。浏览器侧写 {@code ws://host:port/api/msg/ws?token=<ws-ticket>}
     */
    private String path = "/api/msg/ws";

    /**
     * 允许的 Origin，空 = 不限。
     * <p>
     * 这里不设防不是因为偷懒：握手凭据是一次性的短期 ticket，ticket 必须由已认证的
     * HTTP 会话去 {@code /api/msg/inbox/ws-token} 换，跨站页面拿不到这个响应
     * （CORS 挡的是读响应，不是发请求），所以拿到 ticket 的人本来就已经是登录态。
     * 若宿主改成长期 token 走 query，请务必显式配上自己的域名。
     */
    private List<String> allowedOrigins = new ArrayList<String>();

    /**
     * 任何已登录用户都能订阅的 topic，例如 {@code room:lobby}、{@code sys:broadcast}。
     * 未列出的 topic 由 {@code TopicAuthorizationPolicy} 逐个判，全部不表态时按拒绝处理。
     */
    private List<String> publicTopics = new ArrayList<String>();

    /**
     * 连接空闲多久后由容器断开（秒）。客户端的 ping 会刷新它。
     */
    private int idleTimeoutSeconds = 120;

    /**
     * 单帧文本上限（字节）。超过直接断连，不给超大帧留内存。
     */
    private int maxTextMessageBytes = 262144;

    /**
     * 同一用户最多保持几条在线连接，超出时关掉最早的那条。0 或负数 = 不限。
     * <p>
     * 默认给个上限：浏览器刷新、标签页泄漏、移动端重连都会留下半死连接，
     * 没有上限时注册表只会变大不会变小。
     */
    private int maxSessionsPerUser = 8;

    /**
     * 单连接可持有的 topic 数上限，0 或负数 = 不限。
     * <p>
     * 每个 topic 在注册表里都是一个连接集合，一个脚本连上后订阅几十万个
     * {@code room:x} 就能把堆撑爆，所以默认要有这道闸。
     */
    private int maxTopicsPerConnection = 64;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<String> getPublicTopics() {
        return publicTopics;
    }

    public void setPublicTopics(List<String> publicTopics) {
        this.publicTopics = publicTopics;
    }

    public int getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(int idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public int getMaxTextMessageBytes() {
        return maxTextMessageBytes;
    }

    public void setMaxTextMessageBytes(int maxTextMessageBytes) {
        this.maxTextMessageBytes = maxTextMessageBytes;
    }

    public int getMaxSessionsPerUser() {
        return maxSessionsPerUser;
    }

    public void setMaxSessionsPerUser(int maxSessionsPerUser) {
        this.maxSessionsPerUser = maxSessionsPerUser;
    }

    /**
     * 单连接可持有的 topic 数上限，0 或负数 = 不限。
     */
    public int getMaxTopicsPerConnection() {
        return maxTopicsPerConnection;
    }

    public void setMaxTopicsPerConnection(int maxTopicsPerConnection) {
        this.maxTopicsPerConnection = maxTopicsPerConnection;
    }
}
