package ai.platform.aiassit.db.engine.core.service;

import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaDeleteRequest;
import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaQueryRequest;

import java.util.List;

public interface DbTableFieldAccessService {

    List<DbTableFieldMetaDTO> list(DbTableFieldMetaQueryRequest request);

    DbTableFieldMetaDTO get(DbTableFieldMetaQueryRequest request);

    DbTableFieldMetaDTO create(DbTableFieldMetaDTO dto);

    DbTableFieldMetaDTO update(DbTableFieldMetaDTO dto);

    Boolean delete(DbTableFieldMetaDeleteRequest request);
}
