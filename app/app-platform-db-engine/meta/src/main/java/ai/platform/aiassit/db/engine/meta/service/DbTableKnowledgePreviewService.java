package ai.platform.aiassit.db.engine.meta.service;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableKnowledgePreviewDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;

import java.util.List;

public interface DbTableKnowledgePreviewService {

    DbTableKnowledgePreviewDTO preview(String sourceKey, String tableName);

    DbTableKnowledgePreviewDTO preview(DbTableMetaDTO tableMeta, List<DbTableFieldMetaDTO> fields);
}
