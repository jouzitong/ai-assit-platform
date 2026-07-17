-- app-platform-user data schema init aggregation
-- Aggregated from data module structure initialization SQL only.
-- Excludes migrate/update scripts.

-- Source module: data-err-code

CREATE TABLE IF NOT EXISTS err_code (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    code INT NOT NULL COMMENT '错误码',
    http_status INT NOT NULL DEFAULT 200 COMMENT 'HTTP状态码',
    description VARCHAR(512) DEFAULT NULL COMMENT '错误说明',
    tags VARCHAR(255) DEFAULT NULL COMMENT '标签，逗号分隔',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    UNIQUE KEY uk_err_code_code (code),
    KEY idx_err_code_http_status (http_status),
    KEY idx_err_code_tags (tags)
) COMMENT='错误码总表';

CREATE TABLE IF NOT EXISTS err_code_i18n (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    err_code INT NOT NULL COMMENT '错误码',
    locale VARCHAR(16) NOT NULL COMMENT '语言标识',
    message_template VARCHAR(1000) DEFAULT NULL COMMENT '错误消息模板',
    description VARCHAR(512) DEFAULT NULL COMMENT '当前语言说明',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    UNIQUE KEY uk_err_code_i18n_code_locale (err_code, locale),
    KEY idx_err_code_i18n_locale (locale)
) COMMENT='错误码国际化描述表';

-- Source module: data-system-settings

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
