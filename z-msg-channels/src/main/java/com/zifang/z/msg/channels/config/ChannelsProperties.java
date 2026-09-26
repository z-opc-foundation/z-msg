package com.zifang.z.msg.channels.config;

import com.zifang.z.msg.api.Channels;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 外部渠道 provider 的凭据/超时配置（前缀 {@code z-msg.channel}）。
 * <p>
 * 本类本身就是 "通道名 -> 配置" 的 Map：yml 里写
 * {@code z-msg.channel.im-dingtalk.token=xxx} 即命中 key {@code im-dingtalk}。
 * key 支持 {@code IM_DINGTALK} / {@code im_dingtalk} / {@code im-dingtalk} 三种写法
 * （{@link #channel(String)} 归一化匹配），与 core 的
 * {@code MessageProperties.channelCfg} 行为一致。
 * <p>
 * 与 core 的 {@code MessageProperties} 共享同一段 yml：core 只认
 * provider/fallback-channels/enabled 三个字段，其余字段由本类消费；
 * 双方对不认识的字段都默认忽略（Spring Boot 的 ignoreUnknownFields=true）。
 * <p>
 * 敏感值（secret / token / appSecret / accessKeySecret）应由宿主用环境变量注入；
 * provider 在任何日志里都不输出它们原文（有专门测试钉住这一点）。
 */
@ConfigurationProperties(prefix = "z-msg.channel")
public class ChannelsProperties extends LinkedHashMap<String, ChannelsProperties.ChannelCfg> {

    private static final long serialVersionUID = 1L;

    /** HTTP 超时缺省值与硬上限（毫秒）：配置再大也会被夹到上限，防"配 0 当无限等"。 */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 5000;
    public static final int MAX_CONNECT_TIMEOUT_MS = 10000;
    public static final int MAX_READ_TIMEOUT_MS = 60000;
    /** 响应体最多读取字节数，超截断（IM/云厂商的回包都是小 JSON）。 */
    public static final int MAX_RESPONSE_BYTES = 65536;

    /**
     * 按通道取配置；未配置时返回一个空对象（字段全 null），调用方据此决定 ready()=false。
     */
    public ChannelCfg channel(String channel) {
        String key = Channels.normalize(channel);
        if (key == null) {
            return new ChannelCfg();
        }
        String normalized = key.replace('-', '_');
        ChannelCfg direct = get(normalized);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, ChannelCfg> e : entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getKey().trim().toUpperCase(Locale.ROOT).replace('-', '_').equals(normalized)) {
                return e.getValue();
            }
        }
        return new ChannelCfg();
    }

    /**
     * 单个通道的配置段。每个字段在哪个 provider 被真正读到，见 README 配置表；
     * 用不到的字段留 null 即可。
     */
    public static class ChannelCfg {

        /**
         * 该通道激活的 provider 名。core 的 MessageProperties 也读这个 key 来选 bean，
         * 本类字段仅为 yml 完整性，provider 实现自身不读它。
         */
        private String provider;

        /**
         * 完整 webhook 地址（钉钉/飞书/企微机器人直接给全 url；给了就优先于 base-url 拼接）
         */
        private String url;

        /**
         * 厂商 API 根地址；测试注入本地 stub server 也靠它。如 https://oapi.dingtalk.com
         */
        private String baseUrl;

        /**
         * 凭据 token：钉钉=机器人 access_token，飞书=hook 尾段 token，企微=key，
         * Slack=bot user token（xoxb-...）
         */
        private String token;

        /**
         * 签名密钥：钉钉加签 secret / 飞书机器人加签密钥
         */
        private String secret;

        /**
         * 微信公众号 appId
         */
        private String appId;

        /**
         * 微信公众号 appSecret
         */
        private String appSecret;

        /**
         * 云厂商 AccessKeyId（阿里云短信）
         */
        private String accessKeyId;

        /**
         * 云厂商 AccessKeySecret（阿里云短信）
         */
        private String accessKeySecret;

        /**
         * 短信签名（如【z-opc】）；消息 param("signName") 可逐条覆盖
         */
        private String signName;

        /**
         * 模板 id：短信 TemplateCode / 公众号 template_id；
         * 消息 param("templateCode") 可逐条覆盖
         */
        private String templateCode;

        /**
         * 云厂商地域，如 cn-hangzhou（阿里云短信 endpoint 路由用）
         */
        private String region;

        /**
         * 签名协议版本（阿里云 RPC 签名为 "1.0"，随公共参数 SignatureVersion 上送）
         */
        private String signatureVersion = "1.0";

        /**
         * Slack 默认会话（channel 名或 id）；消息 receiver 为空时用它
         */
        private String slackChannel;

        private Integer connectTimeoutMs;
        private Integer readTimeoutMs;

        /**
         * HTTP 层重试次数：仅对传输错误/5xx 生效（供应商业务拒绝不重试，那是路由层降级链的事）。
         * 0 = 不重试，硬上限 3。
         */
        private int retries = 0;

        /**
         * true = 该通道改走本模块的录制 mock（不外发，请求记进内存），本地开发与 example 用
         */
        private boolean mock = false;

        public int connectTimeoutMsOrDefault() {
            return clamp(connectTimeoutMs == null ? DEFAULT_CONNECT_TIMEOUT_MS : connectTimeoutMs,
                    DEFAULT_CONNECT_TIMEOUT_MS, MAX_CONNECT_TIMEOUT_MS);
        }

        public int readTimeoutMsOrDefault() {
            return clamp(readTimeoutMs == null ? DEFAULT_READ_TIMEOUT_MS : readTimeoutMs,
                    DEFAULT_READ_TIMEOUT_MS, MAX_READ_TIMEOUT_MS);
        }

        public int retriesCapped() {
            if (retries < 0) {
                return 0;
            }
            return Math.min(retries, 3);
        }

        private static int clamp(int v, int min, int max) {
            // 0 / 负数 = 非法配置，按缺省处理；不许拿它表达"无限等"
            if (v <= 0) {
                return min;
            }
            return Math.min(v, max);
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getSignName() {
            return signName;
        }

        public void setSignName(String signName) {
            this.signName = signName;
        }

        public String getTemplateCode() {
            return templateCode;
        }

        public void setTemplateCode(String templateCode) {
            this.templateCode = templateCode;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getSignatureVersion() {
            return signatureVersion;
        }

        public void setSignatureVersion(String signatureVersion) {
            this.signatureVersion = signatureVersion;
        }

        public String getSlackChannel() {
            return slackChannel;
        }

        public void setSlackChannel(String slackChannel) {
            this.slackChannel = slackChannel;
        }

        public Integer getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(Integer v) {
            this.connectTimeoutMs = v;
        }

        public Integer getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(Integer v) {
            this.readTimeoutMs = v;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }

        public boolean isMock() {
            return mock;
        }

        public void setMock(boolean mock) {
            this.mock = mock;
        }
    }
}
