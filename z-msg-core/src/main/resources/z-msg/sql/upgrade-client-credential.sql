-- ============================================================
-- 客户端凭据表（对外调用方 app + AK）—— 存量库增量升级
--
-- 背景：跨板块只有 HTTP 边界，别的板块 / 脚本要发消息只能打 POST /api/msg/send。
--        那个端点原先只有「布尔开关」一种准入（开着就是未认证投递入口），
--        2026-11-13 起补一条凭据路：调用方持 app + AK，服务端校验后放行。
--
-- ⚠ key_hash 存 AK 的 sha-256 摘要、key_prefix 只留末 8 位：
--   **库里没有 AK 明文**（明文只在创建那一次返回，丢了只能轮换不能找回）。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
-- 回滚：DROP TABLE z_msg_client;（会连带删掉所有客户端凭据，AK 需重新创建）
-- ============================================================

CREATE TABLE IF NOT EXISTS z_msg_client (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    app_code         VARCHAR(64) NOT NULL COMMENT '调用方应用编码',
    app_name         VARCHAR(128)         DEFAULT NULL COMMENT '调用方应用名（展示用）',
    key_hash         VARCHAR(64) NOT NULL COMMENT 'AK 的 sha-256 十六进制摘要',
    key_prefix       VARCHAR(16)          DEFAULT NULL COMMENT 'AK 末 8 位，仅供列表辨认',
    status           INT          NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    expire_time      DATETIME             DEFAULT NULL COMMENT '过期时间；NULL=永不过期',
    daily_quota      INT          NOT NULL DEFAULT 0 COMMENT '每日配额条数；0=不限',
    quota_date       VARCHAR(10)          DEFAULT NULL COMMENT '配额计数所属日期 yyyy-MM-dd',
    quota_used       INT          NOT NULL DEFAULT 0 COMMENT '当日已用量',
    allowed_channels VARCHAR(255)         DEFAULT NULL COMMENT '允许通道，逗号分隔；空=全部',
    ip_whitelist     VARCHAR(255)         DEFAULT NULL COMMENT 'IP 白名单，逗号分隔；空=不限',
    description      VARCHAR(255)         DEFAULT NULL,
    created_time     DATETIME    NOT NULL,
    updated_time     DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_client_hash (key_hash),
    KEY idx_msg_client_app (app_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='对外调用方凭据（app + AK）';
