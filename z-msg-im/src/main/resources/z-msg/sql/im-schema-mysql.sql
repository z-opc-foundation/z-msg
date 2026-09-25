-- z-msg-im 会话表 (MySQL 8 / InnoDB / utf8mb4)

CREATE TABLE IF NOT EXISTS `z_msg_im_conversation` (
    `id`               BIGINT       NOT NULL COMMENT '雪花 id，同时也是 realtime topic room:{id} 的 id 部分',
    `conv_type`        VARCHAR(16)  NOT NULL COMMENT 'SINGLE / GROUP / ROOM',
    `tenant_code`      VARCHAR(64)           DEFAULT NULL COMMENT '租户隔离码，为空表示平台级',
    `title`            VARCHAR(255)          DEFAULT NULL,
    `avatar`           VARCHAR(512)          DEFAULT NULL,
    `owner_user_id`    BIGINT                DEFAULT NULL,
    `last_msg_seq`     BIGINT       NOT NULL DEFAULT 0 COMMENT '会话内 seq 水位，SeqAllocator 原子自增的唯一权威',
    `last_msg_id`      BIGINT                DEFAULT NULL,
    `last_msg_preview` VARCHAR(512)          DEFAULT NULL COMMENT '会话列表展示用的摘要，冗余存储避免每次 join 消息表',
    `member_count`     INT          NOT NULL DEFAULT 0,
    `created_time`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_time`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `uk_pair`          VARCHAR(128)          DEFAULT NULL COMMENT 'SINGLE 专属稳定键：两个 userId 升序拼接，用于幂等建会话',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_im_conv_pair` (`uk_pair`),
    KEY `idx_im_conv_tenant_type` (`tenant_code`, `conv_type`),
    KEY `idx_im_conv_owner` (`owner_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='IM 会话';

CREATE TABLE IF NOT EXISTS `z_msg_im_member` (
    `id`              BIGINT      NOT NULL COMMENT '雪花 id',
    `conversation_id` BIGINT      NOT NULL,
    `user_id`         BIGINT      NOT NULL,
    `role`            VARCHAR(16) NOT NULL DEFAULT 'MEMBER' COMMENT 'OWNER / ADMIN / MEMBER',
    `last_read_seq`   BIGINT      NOT NULL DEFAULT 0 COMMENT '已读游标，只允许单调前进',
    `muted`           TINYINT     NOT NULL DEFAULT 0 COMMENT '1=免打扰，仍计未读只是不弹',
    `push_switch`     TINYINT     NOT NULL DEFAULT 1 COMMENT '0=完全关闭推送（连库都不查）',
    `cleared_seq`     BIGINT      NOT NULL DEFAULT 0 COMMENT '清空聊天记录游标，同步时作为下界',
    `cleared_time`    TIMESTAMP   NULL DEFAULT NULL COMMENT '清空动作发生时间，供 UI 展示',
    `joined_time`     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_time`    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_im_member_conv_user` (`conversation_id`, `user_id`),
    KEY `idx_im_member_user` (`user_id`, `conversation_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='IM 会话成员';

CREATE TABLE IF NOT EXISTS `z_msg_im_message` (
    `id`              BIGINT       NOT NULL COMMENT '雪花 id',
    `conversation_id` BIGINT       NOT NULL,
    `seq`             BIGINT       NOT NULL COMMENT '会话内单调递增，客户端按它做增量拉取与去重',
    `client_msg_id`   VARCHAR(64)           DEFAULT NULL COMMENT '客户端本地 id，重发时命中唯一索引即视为幂等成功',
    `sender_user_id`  BIGINT       NOT NULL,
    `msg_type`        VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT / IMAGE / FILE / AUDIO / SYS',
    `content`         TEXT,
    `at_user_ids`     VARCHAR(1024)         DEFAULT NULL COMMENT '升序逗号分隔的 userId，避免 JSON 列的版本差异',
    `reply_to_seq`    BIGINT                DEFAULT NULL,
    `tenant_code`     VARCHAR(64)           DEFAULT NULL,
    `created_time`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_im_msg_conv_seq` (`conversation_id`, `seq`),
    UNIQUE KEY `uk_im_msg_conv_client` (`conversation_id`, `client_msg_id`),
    KEY `idx_im_msg_conv_seq_asc` (`conversation_id`, `seq`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='IM 消息';

CREATE TABLE IF NOT EXISTS `z_msg_im_read_receipt` (
    `id`              BIGINT     NOT NULL COMMENT '雪花 id',
    `conversation_id` BIGINT     NOT NULL,
    `user_id`         BIGINT     NOT NULL,
    `last_read_seq`   BIGINT     NOT NULL DEFAULT 0,
    `created_time`    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_time`    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_im_receipt_conv_user` (`conversation_id`, `user_id`),
    KEY `idx_im_receipt_conv_seq` (`conversation_id`, `last_read_seq`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='IM 已读回执（展示态：谁读到哪）';
