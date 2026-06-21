-- AI KB 文档模型迁移：从“知识库批次版本”迁到“当前文档 + 文档历史版本”。
--
-- 适用场景：
-- 旧表已经存在，且仍保留 kb_version_id / version_no / draft_version_no / review_status 等历史字段。
-- 这些字段不再由当前代码写入；如果仍为 NOT NULL，会导致当前文档更新保存快照时报错：
-- Field 'kb_version_id' doesn't have a default value。
--
-- 注意：
-- 本脚本面向已存在旧表的环境执行；全新环境直接执行 ai_kb_init.sql 即可。

ALTER TABLE ai_kb_document
    MODIFY COLUMN kb_version_id BIGINT NULL DEFAULT NULL COMMENT '历史字段：旧批次版本 ID，当前文档模型不再使用',
    MODIFY COLUMN draft_version_no INT NULL DEFAULT NULL COMMENT '历史字段：旧草稿版本号，当前文档模型不再使用',
    MODIFY COLUMN review_status INT NULL DEFAULT NULL COMMENT '历史字段：旧审核状态，当前文档模型不再使用',
    MODIFY COLUMN document_version_no INT NULL DEFAULT 1 COMMENT '当前文档版本号';

UPDATE ai_kb_document
SET document_version_no = 1
WHERE document_version_no IS NULL OR document_version_no < 1;

ALTER TABLE ai_kb_document
    MODIFY COLUMN document_version_no INT NOT NULL DEFAULT 1 COMMENT '当前文档版本号';

ALTER TABLE ai_kb_document_version
    MODIFY COLUMN kb_version_id BIGINT NULL DEFAULT NULL COMMENT '历史字段：旧知识库批次版本 ID，文档历史版本模型不再使用',
    MODIFY COLUMN version_no INT NULL DEFAULT NULL COMMENT '历史字段：旧知识库批次版本号，文档历史版本模型不再使用',
    MODIFY COLUMN document_version_no INT NOT NULL COMMENT '文档自身版本号';
