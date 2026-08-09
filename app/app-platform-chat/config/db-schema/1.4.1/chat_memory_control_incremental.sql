-- Chat Memory control-plane incremental migration for 1.4.1.
--
-- This migration repairs deployments where the 1.4.0 script was applied only
-- partially. RAGFlow remains the owner of memory text, extraction output and
-- embeddings; these changes only repair Java control metadata.
--
-- The statements use information_schema checks instead of ADD COLUMN IF NOT
-- EXISTS so they remain usable on MySQL versions that do not support that
-- clause. Run them with the target application database selected.

-- A few older installations do not have the session-policy table at all.
CREATE TABLE IF NOT EXISTS conversation_memory_session_policy
(
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    policy_code           VARCHAR(64)  NOT NULL COMMENT '会话策略业务编码',
    tenant_id             VARCHAR(128) NOT NULL COMMENT '平台可信租户标识',
    user_id               BIGINT       NOT NULL COMMENT '平台可信用户ID',
    session_code          VARCHAR(64)  NOT NULL COMMENT '策略生效会话',
    provider_memory_id    VARCHAR(128) NOT NULL COMMENT 'Provider Memory ID',
    provider_message_id   VARCHAR(128) NOT NULL COMMENT 'Provider消息ID',
    action                VARCHAR(16)  NOT NULL COMMENT 'EXCLUDE/PIN',
    expires_at             DATETIME              DEFAULT NULL COMMENT '可选失效时间',
    create_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by             BIGINT       NOT NULL DEFAULT -1 COMMENT '创建者',
    updated_by             BIGINT       NOT NULL DEFAULT -1 COMMENT '更新者',
    version                BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted                TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_conversation_memory_policy_code (policy_code),
    UNIQUE KEY uk_conversation_memory_policy_target
        (tenant_id, user_id, session_code, provider_memory_id, provider_message_id, action),
    KEY idx_conversation_memory_policy_owner (tenant_id, user_id, session_code)
) COMMENT ='Memory会话级使用策略控制表';

-- conversation_memory_binding columns introduced/used by the 1.4.0 runtime.
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_binding'
      AND column_name = 'retiring_long_term_memory_id'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE conversation_memory_binding ADD COLUMN retiring_long_term_memory_id VARCHAR(128) DEFAULT NULL COMMENT ''正在异步清理的旧RAGFlow长期Memory ID''',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_binding'
      AND column_name = 'schema_version'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE conversation_memory_binding ADD COLUMN schema_version INT NOT NULL DEFAULT 1 COMMENT ''Provider Memory配置版本''',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_binding'
      AND column_name = 'provision_owner'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE conversation_memory_binding ADD COLUMN provision_owner VARCHAR(64) DEFAULT NULL COMMENT ''创建/补偿租约持有者''',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_binding'
      AND column_name = 'provision_lease_until'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE conversation_memory_binding ADD COLUMN provision_lease_until DATETIME DEFAULT NULL COMMENT ''创建/补偿租约到期时间''',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

-- Sync-task source coordinates are control metadata only; no message body is copied.
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_sync_task'
      AND column_name = 'source_memory_id'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE conversation_memory_sync_task ADD COLUMN source_memory_id VARCHAR(128) DEFAULT NULL COMMENT ''来源Provider Memory ID，仅用于控制定位''',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_sync_task'
      AND column_name = 'source_message_id'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE conversation_memory_sync_task ADD COLUMN source_message_id VARCHAR(128) DEFAULT NULL COMMENT ''来源Provider消息ID，仅用于控制定位''',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

-- Repair lifecycle columns when a pre-1.4.0 task table was retained in place.
SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_sync_task'
      AND column_name = 'status'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE conversation_memory_sync_task ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT ''PENDING'' COMMENT ''PENDING/PROCESSING/SUCCEEDED/RETRY/DEAD/UNKNOWN''',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_sync_task'
      AND column_name = 'retry_count'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE conversation_memory_sync_task ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT ''确定性失败重试次数''',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_sync_task'
      AND column_name = 'next_retry_at'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE conversation_memory_sync_task ADD COLUMN next_retry_at DATETIME DEFAULT NULL COMMENT ''下次重试时间''',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_sync_task'
      AND column_name = 'lease_until'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE conversation_memory_sync_task ADD COLUMN lease_until DATETIME DEFAULT NULL COMMENT ''处理租约到期时间''',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_sync_task'
      AND column_name = 'provider_message_id'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE conversation_memory_sync_task ADD COLUMN provider_message_id VARCHAR(128) DEFAULT NULL COMMENT ''Provider消息ID，可空''',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_sync_task'
      AND column_name = 'last_error_code'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE conversation_memory_sync_task ADD COLUMN last_error_code VARCHAR(64) DEFAULT NULL COMMENT ''脱敏稳定错误码''',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_sync_task'
      AND column_name = 'finished_at'
);
SET @ddl = IF(
    @column_exists = 0,
    'ALTER TABLE conversation_memory_sync_task ADD COLUMN finished_at DATETIME DEFAULT NULL COMMENT ''任务完成时间''',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

-- UNKNOWN is a runtime state, not a database enum; no data rewrite is needed.
-- DELETE_MEMORY is a task operation represented by the existing VARCHAR column.

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_sync_task'
      AND index_name = 'idx_conversation_memory_sync_claim'
);
SET @ddl = IF(
    @index_exists = 0,
    'ALTER TABLE conversation_memory_sync_task ADD KEY idx_conversation_memory_sync_claim (status, next_retry_at, id)',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_memory_sync_task'
      AND index_name = 'idx_conversation_memory_sync_source'
);
SET @ddl = IF(
    @index_exists = 0,
    'ALTER TABLE conversation_memory_sync_task ADD KEY idx_conversation_memory_sync_source (tenant_id, user_id, session_code, round_code)',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_round'
      AND index_name = 'idx_conversation_round_recent'
);
SET @ddl = IF(
    @index_exists = 0,
    'ALTER TABLE conversation_round ADD KEY idx_conversation_round_recent (session_code, user_id, id)',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'conversation_message'
      AND index_name = 'idx_conversation_message_round_order'
);
SET @ddl = IF(
    @index_exists = 0,
    'ALTER TABLE conversation_message ADD KEY idx_conversation_message_round_order (round_code, id)',
    'SELECT 1'
);
PREPARE chat_memory_stmt FROM @ddl;
EXECUTE chat_memory_stmt;
DEALLOCATE PREPARE chat_memory_stmt;

SET @ddl = NULL;
SET @column_exists = NULL;
SET @index_exists = NULL;
