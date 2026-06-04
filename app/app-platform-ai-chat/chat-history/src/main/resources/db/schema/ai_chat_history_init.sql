CREATE TABLE IF NOT EXISTS ai_chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    session_code VARCHAR(64) NOT NULL COMMENT '会话编码',
    user_id BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID',
    business_type VARCHAR(32) DEFAULT NULL COMMENT '业务类型',
    session_name VARCHAR(128) DEFAULT NULL COMMENT '会话名称',
    pinned TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_session_code (session_code),
    KEY idx_user_id (user_id)
) COMMENT='AI聊天会话表';

CREATE TABLE IF NOT EXISTS ai_chat_round (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    round_code VARCHAR(64) NOT NULL COMMENT '轮次编码',
    session_code VARCHAR(64) NOT NULL COMMENT '会话编码',
    user_id BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID',
    model_code VARCHAR(64) DEFAULT NULL COMMENT '模型编码',
    actual_model VARCHAR(128) DEFAULT NULL COMMENT '实际调用模型',
    status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_round_code (round_code),
    KEY idx_session_code (session_code),
    KEY idx_user_id (user_id)
) COMMENT='AI聊天轮次表';

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    message_code VARCHAR(64) NOT NULL COMMENT '消息编码',
    round_code VARCHAR(64) NOT NULL COMMENT '轮次编码',
    session_code VARCHAR(64) NOT NULL COMMENT '会话编码',
    role VARCHAR(32) NOT NULL COMMENT '角色：USER/ASSISTANT',
    content MEDIUMTEXT NOT NULL COMMENT '消息内容',
    sort_no INT NOT NULL DEFAULT 1 COMMENT '轮次内顺序',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_message_code (message_code),
    KEY idx_round_code (round_code),
    KEY idx_session_code (session_code)
) COMMENT='AI聊天消息表';
