ALTER TABLE ai_kb_store
    MODIFY COLUMN client_type INT NULL COMMENT '历史字段：客户端由系统参数统一配置',
    ADD COLUMN description VARCHAR(512) DEFAULT NULL COMMENT 'RAGFlow Dataset 描述' AFTER provider_kb_id,
    ADD COLUMN embedding_model VARCHAR(256) DEFAULT NULL COMMENT 'RAGFlow embedding 模型' AFTER description,
    ADD COLUMN permission VARCHAR(32) DEFAULT NULL COMMENT 'RAGFlow Dataset 权限' AFTER embedding_model,
    ADD COLUMN chunk_method VARCHAR(64) DEFAULT NULL COMMENT 'RAGFlow 分片方式' AFTER permission,
    ADD COLUMN parser_config_json MEDIUMTEXT DEFAULT NULL COMMENT 'RAGFlow parser_config JSON' AFTER chunk_method,
    ADD COLUMN parse_type VARCHAR(64) DEFAULT NULL COMMENT 'RAGFlow 自定义解析类型' AFTER parser_config_json,
    ADD COLUMN pipeline_id VARCHAR(128) DEFAULT NULL COMMENT 'RAGFlow ingestion pipeline ID' AFTER parse_type;
