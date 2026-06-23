package ai.platform.aiassit.db.engine.core.service.impl;

import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaDeleteRequest;
import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.api.dto.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.core.service.DbAccessService;
import ai.platform.aiassit.db.engine.core.service.DbTableFieldAccessService;
import ai.platform.aiassit.db.engine.core.support.DefaultDbSourceKeyResolver;
import ai.platform.aiassit.db.engine.executor.spi.model.DbColumnMeta;
import ai.platform.aiassit.db.engine.executor.spi.model.DbTableColumnDefinition;
import ai.platform.aiassit.db.engine.executor.spi.request.DeleteTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.SaveTableColumnsRequest;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class DbTableFieldAccessServiceImpl implements DbTableFieldAccessService {

    private final DbAccessService dbAccessService;
    private final DefaultDbSourceKeyResolver defaultDbSourceKeyResolver;

    public DbTableFieldAccessServiceImpl(
            DbAccessService dbAccessService,
            DefaultDbSourceKeyResolver defaultDbSourceKeyResolver
    ) {
        this.dbAccessService = dbAccessService;
        this.defaultDbSourceKeyResolver = defaultDbSourceKeyResolver;
    }

    @Override
    public List<DbTableFieldMetaDTO> list(DbTableFieldMetaQueryRequest request) {
        String sourceKey = resolveSourceKey(request == null ? null : request.getSourceKey());
        String tableName = requireTableName(request == null ? null : request.getTableName());
        List<DbTableFieldMetaDTO> fields = dbAccessService.listTableColumns(
                        sourceKey,
                        ListTableColumnsRequest.builder()
                                .tableName(tableName)
                                .build()
                ).getColumns().stream()
                .map(this::toDto)
                .toList();
        if (request != null && StringUtils.hasText(request.getColumnName())) {
            String columnName = request.getColumnName().trim();
            return fields.stream()
                    .filter(field -> columnName.equals(field.getColumnName()))
                    .toList();
        }
        return fields;
    }

    @Override
    public DbTableFieldMetaDTO get(DbTableFieldMetaQueryRequest request) {
        List<DbTableFieldMetaDTO> list = list(request);
        if (CollectionUtils.isEmpty(list)) {
            throw BizException.of();
        }
        return list.get(0);
    }

    @Override
    public DbTableFieldMetaDTO create(DbTableFieldMetaDTO dto) {
        validateDto(dto);
        DbTableFieldMetaQueryRequest query = new DbTableFieldMetaQueryRequest();
        query.setSourceKey(dto.getSourceKey());
        query.setTableName(dto.getTableName());
        query.setColumnName(dto.getColumnName());
        if (!CollectionUtils.isEmpty(list(query))) {
            throw BizException.of();
        }
        saveColumn(dto);
        return get(query);
    }

    @Override
    public DbTableFieldMetaDTO update(DbTableFieldMetaDTO dto) {
        validateDto(dto);
        DbTableFieldMetaQueryRequest query = new DbTableFieldMetaQueryRequest();
        query.setSourceKey(dto.getSourceKey());
        query.setTableName(dto.getTableName());
        query.setColumnName(dto.getColumnName());
        get(query);
        saveColumn(dto);
        return get(query);
    }

    @Override
    public Boolean delete(DbTableFieldMetaDeleteRequest request) {
        String sourceKey = resolveSourceKey(request == null ? null : request.getSourceKey());
        String tableName = requireTableName(request == null ? null : request.getTableName());
        String columnName = requireColumnName(request == null ? null : request.getColumnName());
        dbAccessService.deleteTableColumns(
                sourceKey,
                DeleteTableColumnsRequest.builder()
                        .tableName(tableName)
                        .columnNames(List.of(columnName))
                        .build()
        );
        return Boolean.TRUE;
    }

    private void saveColumn(DbTableFieldMetaDTO dto) {
        dbAccessService.saveTableColumns(
                resolveSourceKey(dto.getSourceKey()),
                SaveTableColumnsRequest.builder()
                        .tableName(requireTableName(dto.getTableName()))
                        .columns(List.of(toDefinition(dto)))
                        .build()
        );
    }

    private DbTableColumnDefinition toDefinition(DbTableFieldMetaDTO dto) {
        return DbTableColumnDefinition.builder()
                .columnName(dto.getColumnName())
                .dataType(dto.getDataType())
                .columnLength(dto.getColumnLength())
                .columnPrecision(dto.getColumnPrecision())
                .columnScale(dto.getColumnScale())
                .nullable(dto.getNullable())
                .primaryKey(dto.getPrimaryKey())
                .defaultValue(dto.getDefaultValue())
                .columnComment(dto.getColumnComment())
                .build();
    }

    private DbTableFieldMetaDTO toDto(DbColumnMeta column) {
        DbTableFieldMetaDTO dto = new DbTableFieldMetaDTO();
        dto.setTableName(column.getTableName());
        dto.setColumnName(column.getColumnName());
        dto.setColumnComment(column.getColumnComment());
        dto.setDataType(column.getDataType());
        dto.setColumnLength(column.getColumnLength());
        dto.setColumnPrecision(column.getColumnPrecision());
        dto.setColumnScale(column.getColumnScale());
        dto.setNullable(column.getNullable());
        dto.setPrimaryKey(column.getPrimaryKey());
        dto.setDefaultValue(column.getDefaultValue());
        dto.setOrdinalPosition(column.getOrdinalPosition());
        dto.setEnabled(Boolean.TRUE);
        return dto;
    }

    private void validateDto(DbTableFieldMetaDTO dto) {
        requireTableName(dto == null ? null : dto.getTableName());
        requireColumnName(dto == null ? null : dto.getColumnName());
        if (!StringUtils.hasText(dto == null ? null : dto.getDataType())) {
            throw BizException.of();
        }
    }

    private String resolveSourceKey(String sourceKey) {
        return defaultDbSourceKeyResolver.resolve(sourceKey);
    }

    private String requireTableName(String tableName) {
        if (!StringUtils.hasText(tableName)) {
            throw BizException.of();
        }
        return tableName.trim();
    }

    private String requireColumnName(String columnName) {
        if (!StringUtils.hasText(columnName)) {
            throw BizException.of();
        }
        return columnName.trim();
    }
}
