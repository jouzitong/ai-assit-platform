CREATE TABLE IF NOT EXISTS render_page_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    code VARCHAR(64) NOT NULL COMMENT '分类编码',
    name VARCHAR(128) NOT NULL COMMENT '分类名称',
    parent_code VARCHAR(64) DEFAULT NULL COMMENT '父分类编码，根分类为空',
    path VARCHAR(512) NOT NULL COMMENT '分类路径',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_render_page_category_code (code),
    KEY idx_render_page_category_path (path),
    KEY idx_render_page_category_parent_code (parent_code),
    KEY idx_render_page_category_enabled (enabled)
) COMMENT='渲染页面分类表';

CREATE TABLE IF NOT EXISTS render_page (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    code VARCHAR(64) NOT NULL COMMENT '页面编码',
    name VARCHAR(128) NOT NULL COMMENT '页面名称',
    category_code VARCHAR(64) DEFAULT NULL COMMENT '所属分类编码，可为空',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '页面状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_render_page_code (code),
    KEY idx_render_page_category_code (category_code),
    KEY idx_render_page_status (status)
) COMMENT='渲染页面主表';

CREATE TABLE IF NOT EXISTS render_page_content (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    page_code VARCHAR(64) NOT NULL COMMENT '页面编码',
    content MEDIUMTEXT NOT NULL COMMENT '当前页面JSON内容',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_render_page_content_page_code (page_code)
) COMMENT='渲染页面当前内容表';

CREATE TABLE IF NOT EXISTS render_page_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    page_code VARCHAR(64) NOT NULL COMMENT '页面编码',
    snapshot_version INT NOT NULL DEFAULT 1 COMMENT '快照业务版本号',
    description VARCHAR(512) DEFAULT NULL COMMENT '快照描述',
    content MEDIUMTEXT NOT NULL COMMENT '页面JSON快照',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    KEY idx_render_page_snapshot_page_code (page_code),
    KEY idx_render_page_snapshot_version (page_code, snapshot_version)
) COMMENT='渲染页面快照表';
