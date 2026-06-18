package ai.platform.aiassit.db.engine.meta.service;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableKnowledgeSyncDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableKnowledgeSyncRequest;

public interface DbTableKnowledgeSyncService {

    DbTableKnowledgeSyncDTO sync(DbTableKnowledgeSyncRequest request);
}
