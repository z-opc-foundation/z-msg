-- z-msg 1.1.0 结构（MySQL 5.7 / 8.0）
-- 这份文件是"生产结构"的单一事实来源；H2 版本 schema-h2.sql 必须与它定义同一批表，
-- 由 SchemaParityTest 在测试期钉住，防止只改一份。
-- 约定：全部表名 z_msg_ 前缀，列名下划线（MyBatis-Plus 默认驼峰映射），InnoDB + utf8mb4。

-- ============ 站内信 ============
-- 一条记录 = 一个用户收件箱里的一条消息。同一业务事件发给多个用户会产生多行，
-- 所以幂等约束落在 (user_id, dedup_key) 而不是 msg_id。
CREATE TABLE IF NOT EXISTS z_msg_message (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL COMMENT '收件人',
    msg_id       VARCHAR(64)  NOT NULL COMMENT '全局消息号(UUID)，投递日志/实时帧按它对齐',
    event_type   VARCHAR(64)  NOT NULL COMMENT '业务类型，等于 Message.bizType',
    msg_type     VARCHAR(32)  NOT NULL DEFAULT 'NOTICE' COMMENT 'NOTICE/CHAT/SYS…，前端渲染样式',
    title        VARCHAR(255)          DEFAULT NULL,
    content      TEXT COMMENT '已渲染正文（入库即定稿，不存模板占位符）',
    link_url     VARCHAR(512)          DEFAULT NULL,
    is_read      TINYINT      NOT NULL DEFAULT 0 COMMENT '0=未读 1=已读',
    read_time    DATETIME              DEFAULT NULL COMMENT '置已读的时间，未读为 NULL',
    priority     TINYINT      NOT NULL DEFAULT 0 COMMENT '0=普通 1=重要 2=紧急；>=1 穿过硬静默',
    pinned       TINYINT      NOT NULL DEFAULT 0 COMMENT '置顶',
    dedup_key    VARCHAR(128)          DEFAULT NULL COMMENT '幂等键，NULL 表示不幂等',
    expire_at    DATETIME              DEFAULT NULL COMMENT '到期后列表不再展示（软过期，不物理删）',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '用户删除标记（软删）',
    tenant_code  VARCHAR(64)           DEFAULT NULL,
    domain_code  VARCHAR(64)           DEFAULT NULL,
    created_time DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_user_dedup (user_id, dedup_key),
    KEY idx_msg_user_list (user_id, is_read, created_time),
    KEY idx_msg_user_pinned (user_id, pinned, created_time),
    KEY idx_msg_msgid (msg_id),
    KEY idx_msg_expire (expire_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='站内信';

-- ============ 模板 ============
-- 模板寻址键是 (biz_type, channel) 而不是某个 code 列 —— 与 MessageTemplateEngine 的查询一致。
CREATE TABLE IF NOT EXISTS z_msg_template (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    biz_type     VARCHAR(64)  NOT NULL COMMENT '业务类型 = Message.bizType / templateCode',
    channel      VARCHAR(32)  NOT NULL COMMENT '介质，取值见 Channels 常量',
    subject      VARCHAR(255)          DEFAULT NULL COMMENT '标题模板，${var} 占位',
    content      TEXT COMMENT '正文模板，${var} 占位',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0=草稿 1=启用 2=停用',
    version      INT          NOT NULL DEFAULT 1,
    tenant_code  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '空串而非 NULL：MySQL 唯一索引不约束 NULL，用 NULL 会让同一 (biz_type,channel) 重复建模板',
    domain_code  VARCHAR(64)           DEFAULT NULL,
    created_time DATETIME     NOT NULL,
    updated_time DATETIME     NOT NULL,
    created_by   VARCHAR(64)           DEFAULT NULL,
    updated_by   VARCHAR(64)           DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tpl_biz_channel (biz_type, channel, tenant_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='消息模板（当前生效版本指针）';

-- 多语言：按 (template_id, locale) 覆盖主表文案
CREATE TABLE IF NOT EXISTS z_msg_template_i18n (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    template_id  BIGINT       NOT NULL,
    locale       VARCHAR(16)  NOT NULL COMMENT 'zh_CN / en_US …',
    subject      VARCHAR(255)          DEFAULT NULL,
    content      TEXT,
    created_time DATETIME     NOT NULL,
    updated_time DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tpl_i18n (template_id, locale)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='消息模板多语言';

-- 历史版本：改模板不原地覆盖，留可回溯快照
CREATE TABLE IF NOT EXISTS z_msg_template_version (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    template_id  BIGINT       NOT NULL,
    version      INT          NOT NULL,
    subject      VARCHAR(255)          DEFAULT NULL,
    content      TEXT,
    change_note  VARCHAR(255)          DEFAULT NULL,
    created_time DATETIME     NOT NULL,
    created_by   VARCHAR(64)           DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tpl_ver (template_id, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='消息模板版本快照';

-- ============ 投递日志 ============
CREATE TABLE IF NOT EXISTS z_msg_delivery_log (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    biz_type            VARCHAR(64)          DEFAULT NULL,
    channel             VARCHAR(32) NOT NULL COMMENT '介质',
    provider            VARCHAR(32) NOT NULL COMMENT '厂商；mock 兜底也记，别把 mock 混进成功里',
    receiver            VARCHAR(128)         DEFAULT NULL,
    user_id             BIGINT               DEFAULT NULL,
    template_id         BIGINT               DEFAULT NULL COMMENT '命中了哪份模板，NULL=用了报文自带文案',
    rendered_subject    VARCHAR(255)         DEFAULT NULL,
    rendered_content    TEXT,
    provider_message_id VARCHAR(128)         DEFAULT NULL COMMENT '厂商回执号，排障按它找厂商工单',
    status              TINYINT     NOT NULL DEFAULT 0 COMMENT '1=success 2=failed 3=mock 4=skipped',
    error_code          VARCHAR(64)          DEFAULT NULL,
    error_message       VARCHAR(512)         DEFAULT NULL,
    retry_count         INT         NOT NULL DEFAULT 0,
    max_retry           INT         NOT NULL DEFAULT 0,
    next_retry_time     DATETIME             DEFAULT NULL,
    duration_ms         INT                  DEFAULT NULL COMMENT '本次厂商调用耗时',
    tenant_code         VARCHAR(64)          DEFAULT NULL,
    domain_code         VARCHAR(64)          DEFAULT NULL,
    created_time        DATETIME    NOT NULL,
    updated_time        DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_log_biz_time (biz_type, created_time),
    KEY idx_log_user_time (user_id, created_time),
    KEY idx_log_status_time (status, created_time),
    KEY idx_log_retry (status, next_retry_time),
    KEY idx_log_provider_msg (provider_message_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='逐条投递日志';

-- ============ 批量任务 ============
CREATE TABLE IF NOT EXISTS z_msg_batch_task (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    biz_type       VARCHAR(64) NOT NULL,
    channel        VARCHAR(32) NOT NULL,
    template_id    BIGINT               DEFAULT NULL,
    total_count    INT         NOT NULL DEFAULT 0,
    success_count  INT         NOT NULL DEFAULT 0,
    failed_count   INT         NOT NULL DEFAULT 0,
    status         TINYINT     NOT NULL DEFAULT 0 COMMENT '0=pending 1=running 2=done 3=partial-failed 4=failed 5=cancelled',
    params_json    TEXT COMMENT '渲染参数快照',
    receivers_json  LONGTEXT COMMENT '收件人列表快照；万人级活动不能塞进 VARCHAR',
    error_message  VARCHAR(512)         DEFAULT NULL,
    tenant_code    VARCHAR(64)          DEFAULT NULL,
    domain_code    VARCHAR(64)          DEFAULT NULL,
    created_time   DATETIME    NOT NULL,
    updated_time   DATETIME    NOT NULL,
    created_by     VARCHAR(64)          DEFAULT NULL,
    finished_time  DATETIME             DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_batch_status_time (status, created_time),
    KEY idx_batch_biz_time (biz_type, created_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='批量下发任务';

-- ============ 用户偏好 ============
-- biz_type 用 '*' 表示全局行，与 MsgUserPreferenceService 的"精确行优先、回落全局"一致。
CREATE TABLE IF NOT EXISTS z_msg_user_preference (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    biz_type     VARCHAR(64) NOT NULL COMMENT "'*' = 全局偏好",
    channels     VARCHAR(255)         DEFAULT NULL COMMENT '允许渠道，逗号分隔；空=全部',
    quiet_start  VARCHAR(8)           DEFAULT NULL COMMENT 'HH:mm，静默时段起',
    quiet_end    VARCHAR(8)           DEFAULT NULL COMMENT 'HH:mm，静默时段止',
    tenant_code  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '同样不能留 NULL，见 z_msg_template.tenant_code',
    domain_code  VARCHAR(64)          DEFAULT NULL,
    created_time DATETIME    NOT NULL,
    updated_time DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pref_user_biz (user_id, biz_type, tenant_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户接收偏好与静默时段';
