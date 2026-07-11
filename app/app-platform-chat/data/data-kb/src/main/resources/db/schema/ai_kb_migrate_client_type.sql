ALTER TABLE ai_kb_store
    ADD COLUMN client_type INT DEFAULT NULL COMMENT '知识库客户端类型：1=BAILIAN,2=RAWFLOW' AFTER kb_name;

UPDATE ai_kb_store
SET client_type = 1
WHERE client_type IS NULL;

ALTER TABLE ai_kb_store
    MODIFY COLUMN client_type INT NOT NULL COMMENT '知识库客户端类型：1=BAILIAN,2=RAWFLOW',
    ADD KEY idx_kb_client_type (client_type);
