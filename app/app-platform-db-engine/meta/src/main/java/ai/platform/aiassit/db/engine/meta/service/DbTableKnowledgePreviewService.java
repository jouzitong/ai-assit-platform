package ai.platform.aiassit.db.engine.meta.service;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableKnowledgePreviewDTO;

public interface DbTableKnowledgePreviewService {

    DbTableKnowledgePreviewDTO preview(String sourceKey, String tableName);
}
