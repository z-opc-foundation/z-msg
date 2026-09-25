package com.zifang.z.msg.core.config;

import com.zifang.z.msg.api.Channels;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * z-msg 配置属性
 * <p>
 * application.yml 示例：
 * <pre>
 * z-msg:
 *   enabled: true
 *   sms:
 *     provider: mock              # 兼容 1.0.0 的写法
 *     default-sign: 【z-opc】
 *   email:
 *     provider: mock
 *   channel:
 *     im_dingtalk:
 *       provider: robot           # 该通道激活哪个 ChannelSender.provider()
 *       fallback-providers: [webhook]
 *     push_fcm:
 *       provider: mock
 *   rate-limit:
 *     enabled: true
 *     default-permits-per-second: 50
 *     per-channel:
 *       SMS: 5
 *     per-user-per-minute: 20
 *   retry:
 *     max-attempts: 3
 *     backoff-ms: [100, 500, 2000]
 *     non-retryable: [PROVIDER_NOT_CONFIGURED, INVALID_RECEIVER, TEMPLATE_NOT_FOUND]
 *   inbox:
 *     push-realtime: true
 *     max-page-size: 200
 *   template:
 *     REGISTER: TEMPLATE_REGISTER
 * </pre>
 */
@ConfigurationProperties(prefix = "z-msg")
public class MessageProperties {

    /**
     * 总开关；false 时不注册任何 z-msg bean（1.0.0 里这个开关只存在于 README）
     */
    private boolean enabled = true;

    private Sms sms = new Sms();
    private Email email = new Email();
    private Map<String, String> template = new HashMap<>();
    /**
     * 通道级配置，key 为通道名（大小写不敏感，建议小写）
     */
    private Map<String, Channel> channel = new HashMap<>();
    private RateLimit rateLimit = new RateLimit();
    private Retry retry = new Retry();
    private Inbox inbox = new Inbox();
    private Realtime realtime = new Realtime();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Sms getSms() {
        return sms;
    }

    public void setSms(Sms sms) {
        this.sms = sms;
    }

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }

    public Map<String, String> getTemplate() {
        return template;
    }

    public void setTemplate(Map<String, String> template) {
        this.template = template == null ? new HashMap<String, String>() : template;
    }

    public Map<String, Channel> getChannel() {
        return channel;
    }

    public void setChannel(Map<String, Channel> channel) {
        this.channel = channel == null ? new HashMap<String, Channel>() : channel;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit == null ? new RateLimit() : rateLimit;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry == null ? new Retry() : retry;
    }

    public Inbox getInbox() {
        return inbox;
    }

    public void setInbox(Inbox inbox) {
        this.inbox = inbox == null ? new Inbox() : inbox;
    }

    public Realtime getRealtime() {
        return realtime;
    }

    public void setRealtime(Realtime realtime) {
        this.realtime = realtime == null ? new Realtime() : realtime;
    }

    /**
     * 按通道取配置，支持 {@code IM_DINGTALK} / {@code im_dingtalk} / {@code im-dingtalk} 三种写法。
     *
     * @return 永不返回 null（未配置时给一个空对象）
     */
    public Channel channelCfg(String channel) {
        if (channel == null || this.channel.isEmpty()) {
            return new Channel();
        }
        String upper = channel.trim().toUpperCase();
        Channel c = this.channel.get(upper);
        if (c != null) {
            return c;
        }
        for (Map.Entry<String, Channel> e : this.channel.entrySet()) {
            String k = e.getKey().trim().toUpperCase().replace('-', '_');
            if (k.equals(upper)) {
                return e.getValue();
            }
        }
        return new Channel();
    }

    /**
     * 该通道激活的 provider 名；channel.&lt;X&gt;.provider 优先，其次 1.0.0 的 sms/email.provider，
     * 再其次是 core 内建实现，最后才是 mock。
     * <p>
     * IN_APP 不能落到 mock 兜底：core 自带 {@code InAppChannel}（provider="db"），
     * 站内信是这里唯一一个"实现就在包里"的通道。以前默认值是 mock，于是
     * {@code gateway.send(IN_APP)} 走 {@code MockFallbackSender} 回 success、
     * {@code z_msg_message} 一行没有，收件箱永远空——而且统计里还带着假成功。
     */
    public String providerOf(String channel) {
        String p = channelCfg(channel).getProvider();
        if (p != null && !p.trim().isEmpty()) {
            return p.trim();
        }
        if ("SMS".equalsIgnoreCase(channel)) {
            return sms.getProvider();
        }
        if ("EMAIL".equalsIgnoreCase(channel)) {
            return email.getProvider();
        }
        if (Channels.IN_APP.equalsIgnoreCase(Channels.normalize(channel))) {
            return "db";
        }
        return "mock";
    }

    public static class Channel {
        /**
         * null = 宿主没指定，交给 {@link #providerOf(String)} 按通道选默认。
         * 字段初值写成 "mock" 会让"我没配"和"我要 mock"长得一样，
         * 于是内建实现永远选不上。显式 {@code provider: mock} 仍然有效。
         */
        private String provider;
        /**
         * 主 provider 失败后按顺序降级到这些通道（如 SMS 挂了转 IM_DINGTALK）
         */
        private List<String> fallbackChannels = new ArrayList<>();
        /**
         * 该通道是否允许被 fan-out 自动选中
         */
        private boolean enabled = true;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public List<String> getFallbackChannels() {
            return fallbackChannels;
        }

        public void setFallbackChannels(List<String> fallbackChannels) {
            this.fallbackChannels = fallbackChannels == null ? new ArrayList<String>() : fallbackChannels;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int defaultPermitsPerSecond = 50;
        private Map<String, Integer> perChannel = new HashMap<>();
        /**
         * 同一用户同一通道的每分钟上限，0 = 不限；防止一个用户把整个通道配额吃光
         */
        private int perUserPermitsPerMinute = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDefaultPermitsPerSecond() {
            return defaultPermitsPerSecond;
        }

        public void setDefaultPermitsPerSecond(int v) {
            this.defaultPermitsPerSecond = v;
        }

        public Map<String, Integer> getPerChannel() {
            return perChannel;
        }

        public void setPerChannel(Map<String, Integer> perChannel) {
            this.perChannel = perChannel == null ? new HashMap<String, Integer>() : perChannel;
        }

        public int getPerUserPermitsPerMinute() {
            return perUserPermitsPerMinute;
        }

        public void setPerUserPermitsPerMinute(int v) {
            this.perUserPermitsPerMinute = v;
        }
    }

    public static class Retry {
        /**
         * 除首次之外的重试次数；0 = 不重试
         */
        private int maxAttempts = 2;
        private List<Long> backoffMs = new ArrayList<>(Arrays.asList(200L, 1000L, 3000L));
        private List<String> nonRetryable = new ArrayList<>(Arrays.asList(
                "PROVIDER_NOT_CONFIGURED", "INVALID_RECEIVER", "TEMPLATE_NOT_FOUND",
                "RATE_LIMIT", "PREF_BLOCKED", "QUIET_HOURS", "UNSUPPORTED_CHANNEL"));
        /**
         * 同步重试最多阻塞多久（毫秒）；超过则放弃剩余重试并把 outcome 记为失败，
         * 避免 HTTP 线程被 1s/2s/4s 的退避钉住。
         */
        private long maxBlockingMs = 1500L;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int v) {
            this.maxAttempts = v;
        }

        public List<Long> getBackoffMs() {
            return backoffMs;
        }

        public void setBackoffMs(List<Long> v) {
            this.backoffMs = v == null ? new ArrayList<Long>() : v;
        }

        public List<String> getNonRetryable() {
            return nonRetryable;
        }

        public void setNonRetryable(List<String> v) {
            this.nonRetryable = v == null ? new ArrayList<String>() : v;
        }

        public long getMaxBlockingMs() {
            return maxBlockingMs;
        }

        public void setMaxBlockingMs(long v) {
            this.maxBlockingMs = v;
        }
    }

    /**
     * 实时接入（WebSocket ticket）。
     */
    public static class Realtime {
        /**
         * 签发 ws-ticket 的 HMAC 密钥。留空 = 不签发（fail closed）：
         * 内置默认密钥会让每个用 z-msg 的宿主共用同一把可伪造身份的钥匙。
         */
        private String ticketSecret = "";
        /**
         * ticket 有效期。只够完成一次握手，之后连接身份由 session 持有，
         * 所以给短：泄露的 ticket 窗口小，也不需要引入 ticket 吊销表。
         */
        private int ticketTtlSeconds = 60;
        private String ticketAudience = "z-msg-ws";

        public String getTicketSecret() {
            return ticketSecret;
        }

        public void setTicketSecret(String v) {
            this.ticketSecret = v;
        }

        public int getTicketTtlSeconds() {
            return ticketTtlSeconds;
        }

        public void setTicketTtlSeconds(int v) {
            this.ticketTtlSeconds = v;
        }

        public String getTicketAudience() {
            return ticketAudience;
        }

        public void setTicketAudience(String v) {
            this.ticketAudience = v;
        }
    }

    public static class Inbox {
        /**
         * 站内信落库后是否同时推 WebSocket
         */
        private boolean pushRealtime = true;
        private int maxPageSize = 200;
        private int defaultPageSize = 20;
        /**
         * 过期天数，0 = 不过期
         */
        private int expireDays = 0;

        public boolean isPushRealtime() {
            return pushRealtime;
        }

        public void setPushRealtime(boolean v) {
            this.pushRealtime = v;
        }

        public int getMaxPageSize() {
            return maxPageSize;
        }

        public void setMaxPageSize(int v) {
            this.maxPageSize = v;
        }

        public int getDefaultPageSize() {
            return defaultPageSize;
        }

        public void setDefaultPageSize(int v) {
            this.defaultPageSize = v;
        }

        public int getExpireDays() {
            return expireDays;
        }

        public void setExpireDays(int v) {
            this.expireDays = v;
        }
    }

    public static class Sms {
        /**
         * 当前激活的 provider 名称（"mock" / "aliyun" / "tencent"）
         */
        private String provider = "mock";
        private String defaultSign = "【z-opc】";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getDefaultSign() {
            return defaultSign;
        }

        public void setDefaultSign(String defaultSign) {
            this.defaultSign = defaultSign;
        }
    }

    public static class Email {
        private String provider = "mock";
        private String defaultFrom = "no-reply@z-opc.com";
        /**
         * SMTP 配置（生产用）
         */
        private String smtpHost;
        private Integer smtpPort;
        private String smtpUsername;
        private String smtpPassword;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getDefaultFrom() {
            return defaultFrom;
        }

        public void setDefaultFrom(String defaultFrom) {
            this.defaultFrom = defaultFrom;
        }

        public String getSmtpHost() {
            return smtpHost;
        }

        public void setSmtpHost(String smtpHost) {
            this.smtpHost = smtpHost;
        }

        public String getSmtpUsername() {
            return smtpUsername;
        }

        public void setSmtpUsername(String smtpUsername) {
            this.smtpUsername = smtpUsername;
        }

        public Integer getSmtpPort() {
            return smtpPort;
        }

        public void setSmtpPort(Integer smtpPort) {
            this.smtpPort = smtpPort;
        }

        public String getSmtpPassword() {
            return smtpPassword;
        }

        public void setSmtpPassword(String smtpPassword) {
            this.smtpPassword = smtpPassword;
        }
    }
}
