CREATE TABLE IF NOT EXISTS ai_provider_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    provider_code VARCHAR(64) NOT NULL COMMENT '提供商编码',
    provider_name VARCHAR(128) NOT NULL COMMENT '提供商名称',
    base_url VARCHAR(512) NOT NULL COMMENT '提供商请求基础地址',
    connect_timeout_ms INT NOT NULL DEFAULT 3000 COMMENT '连接超时时间（毫秒）',
    read_timeout_ms INT NOT NULL DEFAULT 30000 COMMENT '读取超时时间（毫秒）',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '启用状态：true启用，false禁用',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记：0未删除，1已删除',
    UNIQUE KEY uk_provider_code (provider_code)
) COMMENT='AI提供商配置表';

CREATE TABLE IF NOT EXISTS ai_model_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    model_code VARCHAR(64) NOT NULL COMMENT '模型编码',
    model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
    provider_code VARCHAR(64) NOT NULL COMMENT '所属提供商编码',
    api_model VARCHAR(128) NOT NULL COMMENT '提供商侧模型标识',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '启用状态：true启用，false禁用',
    api_key VARCHAR(2048) DEFAULT NULL COMMENT 'API Key，当前直接明文存储',
    ext_json JSON DEFAULT NULL COMMENT '扩展配置JSON，例如 token 限额、温度参数等',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记：0未删除，1已删除',
    UNIQUE KEY uk_model_code (model_code),
    KEY idx_provider_code (provider_code)
) COMMENT='AI模型配置表';
