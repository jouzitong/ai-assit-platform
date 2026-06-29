ALTER TABLE ai_model_config
    ADD COLUMN api_key VARCHAR(2048) DEFAULT NULL COMMENT 'API Key，当前直接明文存储' AFTER enabled,
    ADD COLUMN ext_json JSON DEFAULT NULL COMMENT '扩展配置JSON，例如 token 限额、温度参数等' AFTER api_key;

UPDATE ai_model_config m
LEFT JOIN (
    SELECT c1.*
    FROM ai_model_credential c1
    INNER JOIN (
        SELECT model_code, MAX(id) AS max_id
        FROM ai_model_credential
        WHERE deleted = 0
        GROUP BY model_code
    ) latest
        ON latest.max_id = c1.id
) c
    ON c.model_code = m.model_code
SET m.api_key = c.api_key_ciphertext,
    m.ext_json = JSON_OBJECT(
        'capabilityTags', m.capability_tags,
        'maxContextTokens', m.max_context_tokens,
        'maxOutputTokens', m.max_output_tokens,
        'temperatureEnabled', m.temperature_enabled,
        'priority', m.priority,
        'remark', m.remark
    )
WHERE c.id IS NOT NULL;

UPDATE ai_model_config
SET ext_json = JSON_OBJECT(
        'capabilityTags', capability_tags,
        'maxContextTokens', max_context_tokens,
        'maxOutputTokens', max_output_tokens,
        'temperatureEnabled', temperature_enabled,
        'priority', priority,
        'remark', remark
    )
WHERE ext_json IS NULL;

ALTER TABLE ai_model_config
    DROP COLUMN capability_tags,
    DROP COLUMN max_context_tokens,
    DROP COLUMN max_output_tokens,
    DROP COLUMN temperature_enabled,
    DROP COLUMN priority,
    DROP COLUMN remark,
    DROP COLUMN credential_code,
    DROP COLUMN api_key_ciphertext,
    DROP COLUMN api_key_masked,
    DROP COLUMN key_version,
    DROP COLUMN credential_enabled,
    DROP COLUMN expire_at,
    DROP COLUMN credential_remark;

DROP TABLE IF EXISTS ai_model_credential;
