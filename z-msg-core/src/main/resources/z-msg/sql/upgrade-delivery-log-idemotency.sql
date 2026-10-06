-- =============================================================================
-- z_msg_delivery_log：出站幂等从「仅本 JVM」升级为「跨节点」
-- =============================================================================
--
-- 为什么单独一个文件：schema-mysql.sql / schema-h2.sql 都是 CREATE TABLE IF NOT EXISTS，
-- 对一张**已经建好的表**不生效。存量库必须手工执行这里的三步（顺序不能换）。
--
-- 上线前必读（两条都会让脚本失败，失败是好事，别绕过）：
--   1. biz_type 允许为 NULL，而 MySQL 唯一索引里任何一个 NULL 都会让整行不参与去重。
--      所以先查存量有多少行 biz_type IS NULL，有的话要么补值要么接受"这些行不参与去重"。
--   2. 存量行 idempotency_key 一律 NULL，而唯一索引允许多个 NULL —— 新索引不会被存量数据挡住。
--      除非你已经手工往这一列写过值（那时才会撞车，见下面的预检）。
--
-- -----------------------------------------------------------------------------

-- 【步骤 1】预检：建索引前确认没有重复的非 NULL 去重令牌。
-- MySQL:
--   SELECT channel, biz_type, idempotency_key, COUNT(*) c
--     FROM z_msg_delivery_log
--    WHERE idempotency_key IS NOT NULL
--    GROUP BY channel, biz_type, idempotency_key HAVING c > 1;
-- H2 同样语法可用。
-- 新装库的 idempotency_key 全是 NULL，这一步必然返回空 —— 可以直接往下走。
-- 手工写过值的库，先决定保留哪一行（DELETE 其余的），再继续。

-- 【步骤 2】加列。
-- MySQL:
ALTER TABLE z_msg_delivery_log
    ADD COLUMN idempotency_key VARCHAR(191) DEFAULT NULL
    COMMENT '出站幂等位(去重令牌，非裸 key)；仅已送达的行持有，NULL 不占位';

-- H2（开发/测试库，MySQL 不支持 ADD COLUMN IF NOT EXISTS，H2 支持）：
-- ALTER TABLE z_msg_delivery_log ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(191);

-- 【步骤 3】建唯一索引：跨节点去重的仲裁点。
-- 撞索引 = 别处已经占过这个幂等位 = 本次跳过（ChannelRouter 判成 DUPLICATED）。
-- MySQL 索引键长度 (32+64+191)*4 = 1148 字节，远低于 InnoDB 3072 上限；
-- 但若你的库还是 COMPACT/REDUNDANT 行格式（767 字节上限），需要先 ROW_FORMAT=DYNAMIC。
-- MySQL:
ALTER TABLE z_msg_delivery_log
    ADD UNIQUE KEY uk_channel_biz_dedup (channel, biz_type, idempotency_key);

-- H2:
-- CREATE UNIQUE INDEX IF NOT EXISTS uk_channel_biz_dedup
--     ON z_msg_delivery_log (channel, biz_type, idempotency_key);

-- =============================================================================
-- 回滚
-- =============================================================================
-- ALTER TABLE z_msg_delivery_log DROP INDEX uk_channel_biz_dedup;
-- ALTER TABLE z_msg_delivery_log DROP COLUMN idempotency_key;
--
-- 回滚后跨节点幂等会重新失效（退回「仅本 JVM」），但不影响消息正常投递 ——
-- ChannelRouter 在这一步不可用时是 fail-open 的：不占位、照发。