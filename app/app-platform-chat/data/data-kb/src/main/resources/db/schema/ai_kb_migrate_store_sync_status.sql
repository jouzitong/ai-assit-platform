ALTER TABLE ai_kb_store
    ADD COLUMN sync_status INT NOT NULL DEFAULT 2 COMMENT 'RAGFlow 同步状态：1=创建中,2=已同步,3=创建失败,4=更新中,5=更新失败,6=删除中,7=删除失败' AFTER enabled,
    ADD COLUMN sync_error VARCHAR(1024) DEFAULT NULL COMMENT '最近一次 RAGFlow 同步错误' AFTER sync_status,
    ADD COLUMN last_sync_at DATETIME DEFAULT NULL COMMENT '最近一次 RAGFlow 同步时间' AFTER sync_error,
    ADD INDEX idx_kb_sync_status (sync_status);
