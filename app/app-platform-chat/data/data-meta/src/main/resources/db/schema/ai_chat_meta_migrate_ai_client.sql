CREATE TABLE IF NOT EXISTS ai_client_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    client_code VARCHAR(64) NOT NULL COMMENT '客户端编码',
    client_name VARCHAR(128) NOT NULL COMMENT '客户端名称',
    client_type INT NOT NULL COMMENT '对话客户端类型：1=SPRING_AI,2=AI_AGENT',
    base_url VARCHAR(512) DEFAULT NULL COMMENT '提供商请求基础地址',
    api_key VARCHAR(2048) DEFAULT NULL COMMENT 'API Key，当前直接明文存储',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '启用状态',
    ext_json JSON DEFAULT NULL COMMENT '扩展配置JSON',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_client_code (client_code),
    KEY idx_client_type (client_type)
) COMMENT='AI客户端配置表';

ALTER TABLE ai_model_config
    ADD COLUMN client_id BIGINT DEFAULT NULL COMMENT 'AI客户端配置ID' AFTER model_name,
    ADD KEY idx_client_id (client_id),
    ADD UNIQUE KEY uk_client_api_model (client_id, api_model);
