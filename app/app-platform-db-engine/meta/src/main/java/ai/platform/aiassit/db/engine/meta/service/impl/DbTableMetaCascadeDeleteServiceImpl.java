package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableIndexMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaCascadeDeleteResultDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableIndexMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaCascadeDeleteRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbTableFieldMetaService;
import ai.platform.aiassit.db.engine.meta.service.DbTableIndexMetaService;
import ai.platform.aiassit.db.engine.meta.service.DbTableMetaCascadeDeleteService;
import ai.platform.aiassit.db.engine.meta.service.DbTableMetaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class DbTableMetaCascadeDeleteServiceImpl implements DbTableMetaCascadeDeleteService {

    private final DbTableMetaService tableMetaService;
    private final DbTableFieldMetaService fieldMetaService;
    private final DbTableIndexMetaService indexMetaService;

    public DbTableMetaCascadeDeleteServiceImpl(
            DbTableMetaService tableMetaService,
            DbTableFieldMetaService fieldMetaService,
            DbTableIndexMetaService indexMetaService
    ) {
        this.tableMetaService = tableMetaService;
        this.fieldMetaService = fieldMetaService;
        this.indexMetaService = indexMetaService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DbTableMetaCascadeDeleteResultDTO delete(DbTableMetaCascadeDeleteRequest request) {
        String sourceKey = requireText(request == null ? null : request.getSourceKey(), "sourceKey 不能为空");
        String tableName = requireText(request == null ? null : request.getTableName(), "tableName 不能为空");

        List<DbTableFieldMetaDTO> fields = fieldMetaService.queryAll(buildFieldQuery(sourceKey, tableName));
        int deletedFieldCount = 0;
        for (DbTableFieldMetaDTO field : fields) {
            if (field != null && field.getId() != null && fieldMetaService.delete(field.getId())) {
                deletedFieldCount++;
            }
        }

        List<DbTableIndexMetaDTO> indexes = indexMetaService.queryAll(buildIndexQuery(sourceKey, tableName));
        int deletedIndexCount = 0;
        for (DbTableIndexMetaDTO index : indexes) {
            if (index != null && index.getId() != null && indexMetaService.delete(index.getId())) {
                deletedIndexCount++;
            }
        }

        List<DbTableMetaDTO> tables = tableMetaService.queryAll(buildTableQuery(sourceKey, tableName));
        int deletedTableCount = 0;
        for (DbTableMetaDTO table : tables) {
            if (table != null && table.getId() != null && tableMetaService.delete(table.getId())) {
                deletedTableCount++;
            }
        }

        DbTableMetaCascadeDeleteResultDTO result = new DbTableMetaCascadeDeleteResultDTO();
        result.setSourceKey(sourceKey);
        result.setTableName(tableName);
        result.setDeletedFieldCount(deletedFieldCount);
        result.setDeletedIndexCount(deletedIndexCount);
        result.setDeletedTableCount(deletedTableCount);
        return result;
    }

    private DbTableMetaQueryRequest buildTableQuery(String sourceKey, String tableName) {
        DbTableMetaQueryRequest query = new DbTableMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setTableName(tableName);
        query.setSize(Integer.MAX_VALUE);
        return query;
    }

    private DbTableFieldMetaQueryRequest buildFieldQuery(String sourceKey, String tableName) {
        DbTableFieldMetaQueryRequest query = new DbTableFieldMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setTableName(tableName);
        query.setSize(Integer.MAX_VALUE);
        return query;
    }

    private DbTableIndexMetaQueryRequest buildIndexQuery(String sourceKey, String tableName) {
        DbTableIndexMetaQueryRequest query = new DbTableIndexMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setTableName(tableName);
        query.setSize(Integer.MAX_VALUE);
        return query;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
