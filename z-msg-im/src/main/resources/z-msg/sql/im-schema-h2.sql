-- z-msg-im 会话表 (H2 2.x, MODE=MySQL)
-- 与 im-schema-mysql.sql 表集合/列集合保持一致，只去掉 MySQL 专有语法：
-- 反引号、ENGINE/CHARSET、列 COMMENT、ON UPDATE CURRENT_TIMESTAMP（更新时间由服务层显式写，测试不依赖它）

CREATE TABLE IF NOT EXISTS z_msg_im_conversation (
    id               BIGINT       NOT NULL,
    conv_type        VARCHAR(16)  NOT NULL,
    tenant_code      VARCHAR(64)           DEFAULT NULL,
    title            VARCHAR(255)          DEFAULT NULL,
    avatar           VARCHAR(512)          DEFAULT NULL,
    owner_user_id    BIGINT                DEFAULT NULL,
    last_msg_seq     BIGINT       NOT NULL DEFAULT 0,
    last_msg_id      BIGINT                DEFAULT NULL,
    last_msg_preview VARCHAR(512)          DEFAULT NULL,
    member_count     INT          NOT NULL DEFAULT 0,
    created_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uk_pair          VARCHAR(128)          DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_im_conv_pair ON z_msg_im_conversation (uk_pair);
CREATE INDEX IF NOT EXISTS idx_im_conv_tenant_type ON z_msg_im_conversation (tenant_code, conv_type);
CREATE INDEX IF NOT EXISTS idx_im_conv_owner ON z_msg_im_conversation (owner_user_id);

CREATE TABLE IF NOT EXISTS z_msg_im_member (
    id              BIGINT      NOT NULL,
    conversation_id BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    role            VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    last_read_seq   BIGINT      NOT NULL DEFAULT 0,
    muted           TINYINT     NOT NULL DEFAULT 0,
    push_switch     TINYINT     NOT NULL DEFAULT 1,
    cleared_seq     BIGINT      NOT NULL DEFAULT 0,
    cleared_time    TIMESTAMP   NULL DEFAULT NULL,
    joined_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_im_member_conv_user ON z_msg_im_member (conversation_id, user_id);
CREATE INDEX IF NOT EXISTS idx_im_member_user ON z_msg_im_member (user_id, conversation_id);

CREATE TABLE IF NOT EXISTS z_msg_im_message (
    id              BIGINT       NOT NULL,
    conversation_id BIGINT       NOT NULL,
    seq             BIGINT       NOT NULL,
    client_msg_id   VARCHAR(64)           DEFAULT NULL,
    sender_user_id  BIGINT       NOT NULL,
    msg_type        VARCHAR(16)  NOT NULL DEFAULT 'TEXT',
    content         CLOB,
    at_user_ids     VARCHAR(1024)         DEFAULT NULL,
    reply_to_seq    BIGINT                DEFAULT NULL,
    tenant_code     VARCHAR(64)           DEFAULT NULL,
    created_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_im_msg_conv_seq ON z_msg_im_message (conversation_id, seq);
CREATE UNIQUE INDEX IF NOT EXISTS uk_im_msg_conv_client ON z_msg_im_message (conversation_id, client_msg_id);
CREATE INDEX IF NOT EXISTS idx_im_msg_conv_seq_asc ON z_msg_im_message (conversation_id, seq);

CREATE TABLE IF NOT EXISTS z_msg_im_read_receipt (
    id              BIGINT      NOT NULL,
    conversation_id BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    last_read_seq   BIGINT      NOT NULL DEFAULT 0,
    created_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_im_receipt_conv_user ON z_msg_im_read_receipt (conversation_id, user_id);
CREATE INDEX IF NOT EXISTS idx_im_receipt_conv_seq ON z_msg_im_read_receipt (conversation_id, last_read_seq);
