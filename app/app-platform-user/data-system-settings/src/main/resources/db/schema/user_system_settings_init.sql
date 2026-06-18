CREATE TABLE IF NOT EXISTS system_settings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    setting_key VARCHAR(128) NOT NULL COMMENT '系统配置唯一键',
    description VARCHAR(512) DEFAULT NULL COMMENT '配置说明',
    setting_value TEXT DEFAULT NULL COMMENT '配置值',
    value_type VARCHAR(32) NOT NULL DEFAULT 'STRING' COMMENT '配置值类型',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    UNIQUE KEY uk_setting_key (setting_key),
    KEY idx_value_type (value_type),
    KEY idx_enabled (enabled)
) COMMENT='系统配置表';
