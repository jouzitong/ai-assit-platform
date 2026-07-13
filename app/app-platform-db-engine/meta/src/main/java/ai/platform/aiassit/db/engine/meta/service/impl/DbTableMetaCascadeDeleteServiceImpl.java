package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.api.constant.DbEngineBizCodeConstant;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableIndexMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaCascadeDeleteResultDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableRelationMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableIndexMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaCascadeDeleteRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableRelationMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbTableFieldMetaService;
import ai.platform.aiassit.db.engine.meta.service.DbTableIndexMetaService;
import ai.platform.aiassit.db.engine.meta.service.DbTableMetaCascadeDeleteService;
import ai.platform.aiassit.db.engine.meta.service.DbTableMetaService;
import ai.platform.aiassit.db.engine.meta.service.DbTableRelationMetaService;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DbTableMetaCascadeDeleteServiceImpl implements DbTableMetaCascadeDeleteService {

    private final DbTableMetaService tableMetaService;
    private final DbTableFieldMetaService fieldMetaService;
    private final DbTableIndexMetaService indexMetaService;
    private final DbTableRelationMetaService relationMetaService;

    public DbTableMetaCascadeDeleteServiceImpl(
            DbTableMetaService tableMetaService,
            DbTableFieldMetaService fieldMetaService,
            DbTableIndexMetaService indexMetaService,
            DbTableRelationMetaService relationMetaService
    ) {
        this.tableMetaService = tableMetaService;
        this.fieldMetaService = fieldMetaService;
        this.indexMetaService = indexMetaService;
        this.relationMetaService = relationMetaService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DbTableMetaCascadeDeleteResultDTO delete(DbTableMetaCascadeDeleteRequest request) {
        String sourceKey = requireText(request == null ? null : request.getSourceKey(),
                DbEngineBizCodeConstant.REQUIRED_SOURCE_KEY);
        String tableName = requireText(request == null ? null : request.getTableName(),
                DbEngineBizCodeConstant.REQUIRED_TABLE_NAME);

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

        Set<Long> relationIds = new LinkedHashSet<>();
        collectRelationIds(relationIds,
                relationMetaService.queryAll(buildSourceRelationQuery(sourceKey, tableName)));
        collectRelationIds(relationIds,
                relationMetaService.queryAll(buildTargetRelationQuery(sourceKey, tableName)));
        int deletedRelationCount = 0;
        for (Long relationId : relationIds) {
            if (relationMetaService.delete(relationId)) {
                deletedRelationCount++;
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
        result.setDeletedRelationCount(deletedRelationCount);
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

    private DbTableRelationMetaQueryRequest buildSourceRelationQuery(String sourceKey, String tableName) {
        DbTableRelationMetaQueryRequest query = new DbTableRelationMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setSourceTableName(tableName);
        query.setSize(Integer.MAX_VALUE);
        return query;
    }

    private DbTableRelationMetaQueryRequest buildTargetRelationQuery(String sourceKey, String tableName) {
        DbTableRelationMetaQueryRequest query = new DbTableRelationMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setTargetTableName(tableName);
        query.setSize(Integer.MAX_VALUE);
        return query;
    }

    private void collectRelationIds(Set<Long> relationIds, List<DbTableRelationMetaDTO> relations) {
        for (DbTableRelationMetaDTO relation : relations) {
            if (relation != null && relation.getId() != null) {
                relationIds.add(relation.getId());
            }
        }
    }

    private String requireText(String value, Integer code) {
        if (!StringUtils.hasText(value)) {
            throw BizException.illegalParam(code);
        }
        return value.trim();
    }
}
