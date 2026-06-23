package ai.platform.aiassit.db.engine.meta.service;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaCascadeDeleteResultDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaCascadeDeleteRequest;

public interface DbTableMetaCascadeDeleteService {

    DbTableMetaCascadeDeleteResultDTO delete(DbTableMetaCascadeDeleteRequest request);
}
