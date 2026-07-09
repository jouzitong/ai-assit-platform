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
