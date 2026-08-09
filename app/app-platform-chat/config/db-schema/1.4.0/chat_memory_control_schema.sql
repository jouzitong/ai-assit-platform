-- Chat Memory control-plane schema for 1.4.0.
-- RAGFlow remains the sole owner of memory text, extraction output and embeddings.

CREATE TABLE IF NOT EXISTS conversation_memory_binding
(
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    binding_code          VARCHAR(64)  NOT NULL COMMENT 'Memory绑定业务编码',
    tenant_id             VARCHAR(128) NOT NULL COMMENT '平台可信租户标识',
    user_id               BIGINT       NOT NULL COMMENT '平台可信用户ID',
    provider_type         VARCHAR(32)  NOT NULL COMMENT 'Memory Provider类型',
    client_key            VARCHAR(64)  NOT NULL COMMENT '系统参数中的Provider客户端key',
    session_memory_id     VARCHAR(128)          DEFAULT NULL COMMENT 'RAGFlow会话Memory ID',
    long_term_memory_id   VARCHAR(128)          DEFAULT NULL COMMENT 'RAGFlow长期Memory ID',
    retiring_long_term_memory_id VARCHAR(128)   DEFAULT NULL COMMENT '正在异步清理的旧RAGFlow长期Memory ID',
    schema_version        INT          NOT NULL DEFAULT 1 COMMENT 'Provider Memory配置版本',
    status                VARCHAR(32)  NOT NULL DEFAULT 'CREATING' COMMENT 'CREATING/ACTIVE/MIGRATING/DISABLED/FAILED',
    last_verified_at      DATETIME              DEFAULT NULL COMMENT '最近Provider契约校验时间',
    provision_owner       VARCHAR(64)           DEFAULT NULL COMMENT '创建/补偿租约持有者',
    provision_lease_until DATETIME              DEFAULT NULL COMMENT '创建/补偿租约到期时间',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by            BIGINT       NOT NULL DEFAULT -1 COMMENT '创建者',
    updated_by            BIGINT       NOT NULL DEFAULT -1 COMMENT '更新者',
    version               BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted               TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_conversation_memory_binding_code (binding_code),
    UNIQUE KEY uk_conversation_memory_binding_owner (tenant_id, user_id, provider_type, client_key),
    UNIQUE KEY uk_conversation_memory_binding_session_memory (session_memory_id),
    UNIQUE KEY uk_conversation_memory_binding_longterm_memory (long_term_memory_id)
) COMMENT ='外部Memory资源绑定控制表';

CREATE TABLE IF NOT EXISTS conversation_memory_sync_task
(
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_code             VARCHAR(64)  NOT NULL COMMENT '同步任务业务编码',
    tenant_id             VARCHAR(128) NOT NULL COMMENT '平台可信租户标识',
    user_id               BIGINT       NOT NULL COMMENT '平台可信用户ID',
    session_code          VARCHAR(64)  NOT NULL COMMENT '权威会话定位',
    round_code            VARCHAR(64)           DEFAULT NULL COMMENT '权威轮次定位',
    target_scope          VARCHAR(32)  NOT NULL COMMENT 'SESSION/LONG_TERM',
    target_memory_id      VARCHAR(128)          DEFAULT NULL COMMENT '目标Provider Memory ID',
    source_memory_id      VARCHAR(128)          DEFAULT NULL COMMENT '来源Provider Memory ID，仅用于控制定位',
    source_message_id     VARCHAR(128)          DEFAULT NULL COMMENT '来源Provider消息ID，仅用于控制定位',
    operation             VARCHAR(32)  NOT NULL COMMENT 'ADD_ROUND/SET_STATUS/FORGET/PROMOTE/DELETE_SESSION/DELETE_MEMORY',
    source_version        BIGINT       NOT NULL DEFAULT 1 COMMENT '来源事实版本',
    idempotency_key       CHAR(64)     NOT NULL COMMENT '本地幂等摘要',
    status                VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCEEDED/RETRY/DEAD/UNKNOWN',
    retry_count           INT          NOT NULL DEFAULT 0 COMMENT '确定性失败重试次数',
    next_retry_at         DATETIME              DEFAULT NULL COMMENT '下次重试时间',
    lease_until           DATETIME              DEFAULT NULL COMMENT '处理租约到期时间',
    provider_message_id   VARCHAR(128)          DEFAULT NULL COMMENT 'Provider消息ID，可空',
    last_error_code       VARCHAR(64)           DEFAULT NULL COMMENT '脱敏稳定错误码',
    finished_at           DATETIME              DEFAULT NULL COMMENT '任务完成时间',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by            BIGINT       NOT NULL DEFAULT -1 COMMENT '创建者',
    updated_by            BIGINT       NOT NULL DEFAULT -1 COMMENT '更新者',
    version               BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted               TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_conversation_memory_sync_task_code (task_code),
    UNIQUE KEY uk_conversation_memory_sync_idempotency (idempotency_key),
    KEY idx_conversation_memory_sync_claim (status, next_retry_at, id),
    KEY idx_conversation_memory_sync_source (tenant_id, user_id, session_code, round_code)
) COMMENT ='RAGFlow Memory可靠投递控制任务';

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
    expires_at            DATETIME              DEFAULT NULL COMMENT '可选失效时间',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by            BIGINT       NOT NULL DEFAULT -1 COMMENT '创建者',
    updated_by            BIGINT       NOT NULL DEFAULT -1 COMMENT '更新者',
    version               BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted               TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_conversation_memory_policy_code (policy_code),
    UNIQUE KEY uk_conversation_memory_policy_target
        (tenant_id, user_id, session_code, provider_memory_id, provider_message_id, action),
    KEY idx_conversation_memory_policy_owner (tenant_id, user_id, session_code)
) COMMENT ='Memory会话级使用策略控制表';

-- Recent-window queries must remain index-backed for long conversations.
ALTER TABLE conversation_round
    ADD KEY idx_conversation_round_recent (session_code, user_id, id);

ALTER TABLE conversation_message
    ADD KEY idx_conversation_message_round_order (round_code, id);
