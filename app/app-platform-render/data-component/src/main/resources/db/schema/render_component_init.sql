CREATE TABLE IF NOT EXISTS render_component (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `key` VARCHAR(64) NOT NULL COMMENT '组件唯一标识',
    name VARCHAR(128) NOT NULL COMMENT '组件名称',
    category VARCHAR(64) DEFAULT NULL COMMENT '组件分类',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '组件状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_render_component_key (`key`),
    KEY idx_render_component_category (category),
    KEY idx_render_component_status (status)
) COMMENT='渲染组件主表';

CREATE TABLE IF NOT EXISTS render_component_content (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    component_key VARCHAR(64) NOT NULL COMMENT '组件唯一标识',
    doc_markdown MEDIUMTEXT DEFAULT NULL COMMENT '组件说明文档',
    example_json MEDIUMTEXT DEFAULT NULL COMMENT '组件示例JSON',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_render_component_content_key (component_key)
) COMMENT='渲染组件当前内容表';

CREATE TABLE IF NOT EXISTS render_component_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    component_key VARCHAR(64) NOT NULL COMMENT '组件唯一标识',
    doc_markdown MEDIUMTEXT DEFAULT NULL COMMENT '组件说明文档快照',
    example_json MEDIUMTEXT DEFAULT NULL COMMENT '组件示例JSON快照',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新者',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    KEY idx_render_component_snapshot_key (component_key)
) COMMENT='渲染组件快照表';
