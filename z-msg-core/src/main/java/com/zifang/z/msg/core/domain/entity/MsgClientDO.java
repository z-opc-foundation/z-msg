package com.zifang.z.msg.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

import java.time.LocalDateTime;

/**
 * 客户端凭据 DO（对外调用方用 AK 调消息服务）
 * <p>
 * 存在的理由：跨板块铁律是板块之间只有 HTTP 边界、没有 jar 边界，别的模块要发消息
 * 只能跨进程打 {@code POST /api/msg/send}；而那个端点原本只有「布尔开关」一种准入，
 * 开着就等于未认证投递入口。现在补一条「凭据准入」的路：调用方持 app + AK，
 * 服务端校验 AK 后把客户端身份放进 request attribute，端点据此放行。
 * <p>
 * <b>为什么不存明文 AK</b>：AK 本身是凭据，仓里对明文密钥零容忍（全仓明文禁令）。
 * 所以库里存 {@code keyHash}（sha-256 十六进制）+ {@code keyPrefix}（末 8 位，只用于列表里辨认），
 * 明文只在创建那一刻返回一次，之后谁都拿不回去 —— 丢了就重建，不找回。
 */
@BizModel(name = "客户端凭据 DO（对外调用方用 AK 调消息服务）", module = "z-msg", description = "客户端凭据 DO（对外调用方用 AK 调消息服务） 存在的理由：跨板块铁律是板块之间只有 HTTP 边界、没有 jar 边界，别的模块要发消息 只能跨进程打 POST /api/msg/send；而那个端点原本只有「布尔开关」一种准入， 开着就等于未认证投递入口。现在补一条「凭据准入」的路：调用方持 app + AK， 服务端校验 AK 后把客户端身份放进 request attribute，端点据此放行。 为什么不存明文 AK ：AK 本身是凭据，仓里对明文密钥零容忍（全仓明文禁令）。 所以库里存 keyHash（sha-256 十六进制）+ keyPrefix（末 8 位，只用于列表里辨认）， 明文只在创建那一刻返回一次，之后谁都拿不回去 —— 丢了就重建，不找回。")
@TableName("z_msg_client")
public class MsgClientDO {

    @TableId(type = IdType.AUTO)
    @BizField(name = "id", type = BizField.FieldType.INTEGER)
    private Long id;
    /**
     * 调用方应用编码（如 z-script / z-observer / 外部系统）。
     */
    @BizField(name = "调用方应用编码（如 z-script / z-observer / 外部系统）", type = BizField.FieldType.STRING)
    private String appCode;
    /**
     * 调用方应用名（展示用）。
     */
    @BizField(name = "调用方应用名（展示用）", type = BizField.FieldType.STRING)
    private String appName;
    /**
     * AK 的 sha-256 十六进制摘要。校验时对来头做同样摘要再比对。
     */
    @BizField(name = "AK 的 sha-256 十六进制摘要。校验时对来头做同样摘要再比对", type = BizField.FieldType.STRING)
    private String keyHash;
    /**
     * AK 末 8 位，仅供列表辨认，不可用于认证。
     */
    @BizField(name = "AK 末 8 位，仅供列表辨认，不可用于认证", type = BizField.FieldType.STRING)
    private String keyPrefix;
    /**
     * 1=启用，0=停用。
     */
    @BizField(name = "1=启用 0=停用", type = BizField.FieldType.INTEGER)
    private Integer status;
    /**
     * 过期时间；null = 永不过期。
     */
    @BizField(name = "过期时间；NULL=永不过期", type = BizField.FieldType.DATETIME)
    private LocalDateTime expireTime;
    /**
     * 每日配额（条）；0 或 null = 不限。
     */
    @BizField(name = "每日配额条数；0=不限", type = BizField.FieldType.INTEGER)
    private Integer dailyQuota;
    /**
     * 配额已计入的日期（yyyy-MM-dd）。跨天自动清零，所以它和 {@link #quotaUsed} 是一对。
     */
    @BizField(name = "配额计数所属日期 yyyy-MM-dd", type = BizField.FieldType.STRING)
    private String quotaDate;
    /**
     * 当日已用量。
     */
    @BizField(name = "当日已用量", type = BizField.FieldType.INTEGER)
    private Integer quotaUsed;
    /**
     * 允许的通道，逗号分隔；空 = 全部通道。
     */
    @BizField(name = "允许通道，逗号分隔；空=全部", type = BizField.FieldType.STRING)
    private String allowedChannels;
    /**
     * IP 白名单，逗号分隔；空 = 不限。
     */
    @BizField(name = "IP 白名单，逗号分隔；空=不限", type = BizField.FieldType.STRING)
    private String ipWhitelist;
    /**
     * 备注。
     */
    @BizField(name = "备注", type = BizField.FieldType.STRING)
    private String description;
    @BizField(name = "createdTime", type = BizField.FieldType.DATETIME)
    private LocalDateTime createdTime;
    @BizField(name = "updatedTime", type = BizField.FieldType.DATETIME)
    private LocalDateTime updatedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public Integer getDailyQuota() {
        return dailyQuota;
    }

    public void setDailyQuota(Integer dailyQuota) {
        this.dailyQuota = dailyQuota;
    }

    public String getQuotaDate() {
        return quotaDate;
    }

    public void setQuotaDate(String quotaDate) {
        this.quotaDate = quotaDate;
    }

    public Integer getQuotaUsed() {
        return quotaUsed;
    }

    public void setQuotaUsed(Integer quotaUsed) {
        this.quotaUsed = quotaUsed;
    }

    public String getAllowedChannels() {
        return allowedChannels;
    }

    public void setAllowedChannels(String allowedChannels) {
        this.allowedChannels = allowedChannels;
    }

    public String getIpWhitelist() {
        return ipWhitelist;
    }

    public void setIpWhitelist(String ipWhitelist) {
        this.ipWhitelist = ipWhitelist;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }
}



