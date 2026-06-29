-- AI KB 文档来源系统字段迁移：source_system -> meta_json.sourceSystem
--
-- 适用场景：
-- 旧表 ai_kb_document / ai_kb_document_version 已存在 source_system 列，
-- 现将其并入 meta_json，避免单独维护业务侧附加字段。
--
-- 注意：
-- 1. 新环境直接执行 ai_kb_init.sql 即可，不需要本脚本。
-- 2. 本脚本会尽量保留既有 meta_json 内容，仅在存在 source_system 且 meta_json 未显式携带
--    sourceSystem 时补写该字段，随后删除独立列。

UPDATE ai_kb_document
SET meta_json = CASE
    WHEN source_system IS NULL OR TRIM(source_system) = '' THEN meta_json
    WHEN meta_json IS NULL OR TRIM(meta_json) = '' THEN JSON_OBJECT('sourceSystem', TRIM(source_system))
    WHEN JSON_VALID(meta_json) THEN JSON_SET(meta_json, '$.sourceSystem', COALESCE(JSON_EXTRACT(meta_json, '$.sourceSystem'), JSON_QUOTE(TRIM(source_system))))
    ELSE meta_json
END
WHERE source_system IS NOT NULL AND TRIM(source_system) <> '';

UPDATE ai_kb_document_version
SET meta_json = CASE
    WHEN source_system IS NULL OR TRIM(source_system) = '' THEN meta_json
    WHEN meta_json IS NULL OR TRIM(meta_json) = '' THEN JSON_OBJECT('sourceSystem', TRIM(source_system))
    WHEN JSON_VALID(meta_json) THEN JSON_SET(meta_json, '$.sourceSystem', COALESCE(JSON_EXTRACT(meta_json, '$.sourceSystem'), JSON_QUOTE(TRIM(source_system))))
    ELSE meta_json
END
WHERE source_system IS NOT NULL AND TRIM(source_system) <> '';

ALTER TABLE ai_kb_document
    DROP COLUMN source_system;

ALTER TABLE ai_kb_document_version
    DROP COLUMN source_system;
