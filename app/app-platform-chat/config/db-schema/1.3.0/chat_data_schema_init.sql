-- Chat session group incremental schema for 1.3.0.
-- Apply after the published 1.0.0 chat schema and earlier chat migrations.

CREATE TABLE IF NOT EXISTS conversation_group
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    group_code  VARCHAR(64)  NOT NULL COMMENT '分组业务编码',
    user_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '分组所属用户ID',
    group_name  VARCHAR(128) NOT NULL COMMENT '分组名称',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT       NOT NULL DEFAULT 0 COMMENT '创建者',
    updated_by  BIGINT       NOT NULL DEFAULT 0 COMMENT '更新者',
    version     BIGINT       NOT NULL DEFAULT 1 COMMENT '版本号',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    UNIQUE KEY uk_group_code (group_code),
    KEY idx_group_user (user_id)
) COMMENT ='聊天会话分组表';

ALTER TABLE conversation_session
    ADD COLUMN group_code VARCHAR(64) NULL COMMENT '所属分组编码，NULL表示未分组',
    ADD KEY idx_session_user_group (user_id, group_code);
