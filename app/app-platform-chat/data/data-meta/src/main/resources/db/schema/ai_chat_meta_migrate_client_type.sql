ALTER TABLE ai_model_config
    ADD COLUMN client_type INT DEFAULT NULL COMMENT '对话客户端类型：1=SPRING_AI,2=AI_AGENT' AFTER model_name;

UPDATE ai_model_config
SET client_type = CASE UPPER(TRIM(provider_code))
    WHEN 'AI_AGENT' THEN 2
    WHEN 'OPENAI' THEN 1
    WHEN 'DASHSCOPE' THEN 1
    WHEN 'QWEN' THEN 1
    WHEN 'DEEPSEEK' THEN 1
    WHEN 'OLLAMA' THEN 1
    WHEN 'CUSTOM' THEN 1
    ELSE client_type
END
WHERE client_type IS NULL;

-- 历史自定义 Provider 当前均由通用 Spring AI Driver 承接。
UPDATE ai_model_config
SET client_type = 1
WHERE client_type IS NULL;

ALTER TABLE ai_model_config
    DROP INDEX idx_provider_code,
    DROP COLUMN provider_code,
    DROP COLUMN provider_name,
    MODIFY COLUMN client_type INT NOT NULL COMMENT '对话客户端类型：1=SPRING_AI,2=AI_AGENT',
    ADD KEY idx_client_type (client_type);
