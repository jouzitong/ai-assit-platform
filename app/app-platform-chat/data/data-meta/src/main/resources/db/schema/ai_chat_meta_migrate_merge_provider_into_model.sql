ALTER TABLE ai_model_config
    ADD COLUMN provider_name VARCHAR(128) DEFAULT NULL COMMENT '提供商名称' AFTER provider_code,
    ADD COLUMN base_url VARCHAR(512) DEFAULT NULL COMMENT '提供商请求基础地址' AFTER provider_name;

UPDATE ai_model_config m
INNER JOIN ai_provider_config p
    ON p.provider_code = m.provider_code
SET m.provider_name = p.provider_name,
    m.base_url = p.base_url
WHERE (m.provider_name IS NULL OR m.provider_name = '')
   OR (m.base_url IS NULL OR m.base_url = '');

ALTER TABLE ai_model_config
    MODIFY COLUMN provider_code VARCHAR(64) DEFAULT NULL COMMENT '所属提供商编码',
    MODIFY COLUMN provider_name VARCHAR(128) DEFAULT NULL COMMENT '提供商名称',
    MODIFY COLUMN base_url VARCHAR(512) DEFAULT NULL COMMENT '提供商请求基础地址';

DROP TABLE IF EXISTS ai_provider_config;
