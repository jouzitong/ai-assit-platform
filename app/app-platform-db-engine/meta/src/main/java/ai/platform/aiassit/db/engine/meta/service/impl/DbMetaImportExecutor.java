package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.entity.DbTableFieldMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.DbTableIndexMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.DbTableMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportResultDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableIndexMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.importer.DbMetaImportData;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableIndexMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaQueryRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Service
public class DbMetaImportExecutor {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DbTableMetaServiceImpl tableMetaService;
    private final DbTableFieldMetaServiceImpl fieldMetaService;
    private final DbTableIndexMetaServiceImpl indexMetaService;

    public DbMetaImportExecutor(
            DbTableMetaServiceImpl tableMetaService,
            DbTableFieldMetaServiceImpl fieldMetaService,
            DbTableIndexMetaServiceImpl indexMetaService
    ) {
        this.tableMetaService = tableMetaService;
        this.fieldMetaService = fieldMetaService;
        this.indexMetaService = indexMetaService;
    }

    @Transactional(rollbackFor = Exception.class)
    public DbMetaImportResultDTO importData(String requestSourceKey, MultipartFile file, String format, DbMetaImportData importData) throws java.io.IOException {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        long fileSize = file == null ? 0L : file.getSize();
        log.info("开始导入数据库元数据, format={}, sourceKey={}, fileName={}, fileSize={}", format, requestSourceKey, originalFilename, fileSize);
        try {
            int tableCreatedCount = 0;
            int tableUpdatedCount = 0;
            for (DbMetaImportData.TableRow row : importData.getTables()) {
                row.setSourceKey(resolveSourceKey(requestSourceKey, row.getSourceKey()));
                DbTableMetaDTO existing = findExistingTable(row.getSourceKey(), row.getTableName());
                if (existing == null) {
                    tableMetaService.add(toTableDto(row, null));
                    tableCreatedCount++;
                } else {
                    updateExistingTable(existing.getId(), row);
                    tableUpdatedCount++;
                }
            }

            int fieldCreatedCount = 0;
            int fieldUpdatedCount = 0;
            for (DbMetaImportData.FieldRow row : importData.getFields()) {
                row.setSourceKey(resolveSourceKey(requestSourceKey, row.getSourceKey()));
                DbTableFieldMetaDTO existing = findExistingField(row.getSourceKey(), row.getTableName(), row.getColumnName());
                if (existing == null) {
                    fieldMetaService.add(toFieldDto(row, null));
                    fieldCreatedCount++;
                } else {
                    updateExistingField(existing.getId(), row);
                    fieldUpdatedCount++;
                }
            }

            int indexCreatedCount = 0;
            int indexUpdatedCount = 0;
            for (DbMetaImportData.IndexRow row : importData.getIndexes()) {
                row.setSourceKey(resolveSourceKey(requestSourceKey, row.getSourceKey()));
                DbTableIndexMetaDTO existing = findExistingIndex(
                        row.getSourceKey(),
                        row.getTableName(),
                        row.getIndexName(),
                        row.getColumnName()
                );
                if (existing == null) {
                    indexMetaService.add(toIndexDto(row, null));
                    indexCreatedCount++;
                } else {
                    updateExistingIndex(existing.getId(), row);
                    indexUpdatedCount++;
                }
            }

            log.info(
                    "数据库元数据导入完成, format={}, sourceKey={}, tableCreatedCount={}, tableUpdatedCount={}, fieldCreatedCount={}, fieldUpdatedCount={}, indexCreatedCount={}, indexUpdatedCount={}",
                    format,
                    requestSourceKey,
                    tableCreatedCount,
                    tableUpdatedCount,
                    fieldCreatedCount,
                    fieldUpdatedCount,
                    indexCreatedCount,
                    indexUpdatedCount
            );
            return DbMetaImportResultDTO.builder()
                    .tableCreatedCount(tableCreatedCount)
                    .tableUpdatedCount(tableUpdatedCount)
                    .fieldCreatedCount(fieldCreatedCount)
                    .fieldUpdatedCount(fieldUpdatedCount)
                    .indexCreatedCount(indexCreatedCount)
                    .indexUpdatedCount(indexUpdatedCount)
                    .build();
        } catch (Exception ex) {
            log.error("数据库元数据导入失败, format={}, sourceKey={}, fileName={}", format, requestSourceKey, originalFilename, ex);
            throw wrapImportException(ex);
        }
    }

    private DbTableMetaDTO findExistingTable(String sourceKey, String tableName) {
        DbTableMetaQueryRequest query = new DbTableMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setTableName(tableName);
        return tableMetaService.get(query);
    }

    private DbTableFieldMetaDTO findExistingField(String sourceKey, String tableName, String columnName) {
        DbTableFieldMetaQueryRequest query = new DbTableFieldMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setTableName(tableName);
        query.setColumnName(columnName);
        List<DbTableFieldMetaDTO> dtoList = fieldMetaService.queryAll(query);
        return dtoList.stream()
                .filter(dto -> StringUtils.hasText(dto.getColumnName()) && dto.getColumnName().equals(columnName))
                .findFirst()
                .orElse(null);
    }

    private DbTableIndexMetaDTO findExistingIndex(String sourceKey, String tableName, String indexName, String columnName) {
        DbTableIndexMetaQueryRequest query = new DbTableIndexMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setTableName(tableName);
        query.setIndexName(indexName);
        List<DbTableIndexMetaDTO> dtoList = indexMetaService.queryAll(query);
        return dtoList.stream()
                .filter(dto -> StringUtils.hasText(dto.getColumnName()) && dto.getColumnName().equals(columnName))
                .findFirst()
                .orElse(null);
    }

    private DbTableMetaDTO toTableDto(DbMetaImportData.TableRow row, DbTableMetaDTO existing) {
        DbTableMetaDTO dto = existing == null ? new DbTableMetaDTO() : existing;
        dto.setSourceKey(row.getSourceKey());
        dto.setTableName(row.getTableName());
        dto.setTableComment(row.getTableComment());
        dto.setTableType(row.getTableType());
        dto.setLayerType(row.getLayerType());
        dto.setRowCount(row.getRowCount());
        dto.setColumnCount(row.getColumnCount());
        dto.setPartitionKey(row.getPartitionKey());
        dto.setFreshnessSeconds(row.getFreshnessSeconds());
        dto.setStatus(row.getStatus());
        dto.setEnabled(defaultBoolean(row.getEnabled()));
        dto.setLastScanAt(parseDateTime(row.getLastScanAt()));
        dto.setLastSyncAt(parseDateTime(row.getLastSyncAt()));
        dto.setRemark(row.getRemark());
        return dto;
    }

    private void updateExistingTable(Long id, DbMetaImportData.TableRow row) {
        boolean updated = tableMetaService.lambdaUpdate()
                .eq(DbTableMetaEntity::getId, id)
                .set(DbTableMetaEntity::getSourceKey, row.getSourceKey())
                .set(DbTableMetaEntity::getTableName, row.getTableName())
                .set(DbTableMetaEntity::getTableComment, row.getTableComment())
                .set(DbTableMetaEntity::getTableType, row.getTableType())
                .set(DbTableMetaEntity::getLayerType, row.getLayerType())
                .set(DbTableMetaEntity::getRowCount, row.getRowCount())
                .set(DbTableMetaEntity::getColumnCount, row.getColumnCount())
                .set(DbTableMetaEntity::getPartitionKey, row.getPartitionKey())
                .set(DbTableMetaEntity::getFreshnessSeconds, row.getFreshnessSeconds())
                .set(DbTableMetaEntity::getStatus, row.getStatus())
                .set(DbTableMetaEntity::getEnabled, defaultBoolean(row.getEnabled()))
                .set(DbTableMetaEntity::getLastScanAt, parseDateTime(row.getLastScanAt()))
                .set(DbTableMetaEntity::getLastSyncAt, parseDateTime(row.getLastSyncAt()))
                .set(DbTableMetaEntity::getRemark, row.getRemark())
                .update();
        if (!updated) {
            throw new IllegalStateException("更新表元数据失败, id=" + id + ", tableName=" + row.getTableName());
        }
    }

    private DbTableFieldMetaDTO toFieldDto(DbMetaImportData.FieldRow row, DbTableFieldMetaDTO existing) {
        DbTableFieldMetaDTO dto = existing == null ? new DbTableFieldMetaDTO() : existing;
        dto.setSourceKey(row.getSourceKey());
        dto.setTableName(row.getTableName());
        dto.setColumnName(row.getColumnName());
        dto.setColumnComment(row.getColumnComment());
        dto.setDataType(row.getDataType());
        dto.setColumnLength(row.getColumnLength());
        dto.setColumnPrecision(row.getColumnPrecision());
        dto.setColumnScale(row.getColumnScale());
        dto.setNullable(defaultBoolean(row.getNullable()));
        dto.setPrimaryKey(defaultBoolean(row.getPrimaryKey()));
        dto.setPartitionKey(defaultBoolean(row.getPartitionKey()));
        dto.setDefaultValue(row.getDefaultValue());
        dto.setOrdinalPosition(row.getOrdinalPosition());
        dto.setFieldRole(row.getFieldRole());
        dto.setEnabled(defaultBoolean(row.getEnabled()));
        dto.setRemark(row.getRemark());
        return dto;
    }

    private void updateExistingField(Long id, DbMetaImportData.FieldRow row) {
        boolean updated = fieldMetaService.lambdaUpdate()
                .eq(DbTableFieldMetaEntity::getId, id)
                .set(DbTableFieldMetaEntity::getSourceKey, row.getSourceKey())
                .set(DbTableFieldMetaEntity::getTableName, row.getTableName())
                .set(DbTableFieldMetaEntity::getColumnName, row.getColumnName())
                .set(DbTableFieldMetaEntity::getColumnComment, row.getColumnComment())
                .set(DbTableFieldMetaEntity::getDataType, row.getDataType())
                .set(DbTableFieldMetaEntity::getColumnLength, row.getColumnLength())
                .set(DbTableFieldMetaEntity::getColumnPrecision, row.getColumnPrecision())
                .set(DbTableFieldMetaEntity::getColumnScale, row.getColumnScale())
                .set(DbTableFieldMetaEntity::getNullable, defaultBoolean(row.getNullable()))
                .set(DbTableFieldMetaEntity::getPrimaryKey, defaultBoolean(row.getPrimaryKey()))
                .set(DbTableFieldMetaEntity::getPartitionKey, defaultBoolean(row.getPartitionKey()))
                .set(DbTableFieldMetaEntity::getDefaultValue, row.getDefaultValue())
                .set(DbTableFieldMetaEntity::getOrdinalPosition, row.getOrdinalPosition())
                .set(DbTableFieldMetaEntity::getFieldRole, row.getFieldRole())
                .set(DbTableFieldMetaEntity::getEnabled, defaultBoolean(row.getEnabled()))
                .set(DbTableFieldMetaEntity::getRemark, row.getRemark())
                .update();
        if (!updated) {
            throw new IllegalStateException("更新字段元数据失败, id=" + id + ", columnName=" + row.getColumnName());
        }
    }

    private DbTableIndexMetaDTO toIndexDto(DbMetaImportData.IndexRow row, DbTableIndexMetaDTO existing) {
        DbTableIndexMetaDTO dto = existing == null ? new DbTableIndexMetaDTO() : existing;
        dto.setSourceKey(row.getSourceKey());
        dto.setTableName(row.getTableName());
        dto.setIndexName(row.getIndexName());
        dto.setIndexType(row.getIndexType());
        dto.setUniqueFlag(defaultBoolean(row.getUniqueFlag()));
        dto.setPrimaryFlag(defaultBoolean(row.getPrimaryFlag()));
        dto.setColumnName(row.getColumnName());
        dto.setColumnOrder(row.getColumnOrder());
        dto.setEnabled(defaultBoolean(row.getEnabled()));
        dto.setRemark(row.getRemark());
        return dto;
    }

    private void updateExistingIndex(Long id, DbMetaImportData.IndexRow row) {
        boolean updated = indexMetaService.lambdaUpdate()
                .eq(DbTableIndexMetaEntity::getId, id)
                .set(DbTableIndexMetaEntity::getSourceKey, row.getSourceKey())
                .set(DbTableIndexMetaEntity::getTableName, row.getTableName())
                .set(DbTableIndexMetaEntity::getIndexName, row.getIndexName())
                .set(DbTableIndexMetaEntity::getIndexType, row.getIndexType())
                .set(DbTableIndexMetaEntity::getUniqueFlag, defaultBoolean(row.getUniqueFlag()))
                .set(DbTableIndexMetaEntity::getPrimaryFlag, defaultBoolean(row.getPrimaryFlag()))
                .set(DbTableIndexMetaEntity::getColumnName, row.getColumnName())
                .set(DbTableIndexMetaEntity::getColumnOrder, row.getColumnOrder())
                .set(DbTableIndexMetaEntity::getEnabled, defaultBoolean(row.getEnabled()))
                .set(DbTableIndexMetaEntity::getRemark, row.getRemark())
                .update();
        if (!updated) {
            throw new IllegalStateException("更新索引元数据失败, id=" + id + ", indexName=" + row.getIndexName());
        }
    }

    private String resolveSourceKey(String requestSourceKey, String rowSourceKey) {
        if (StringUtils.hasText(rowSourceKey)) {
            return rowSourceKey.trim();
        }
        if (StringUtils.hasText(requestSourceKey)) {
            return requestSourceKey.trim();
        }
        throw new IllegalArgumentException("缺少 sourceKey");
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }

    private Boolean defaultBoolean(Boolean value) {
        return value != null ? value : Boolean.TRUE;
    }

    private RuntimeException wrapImportException(Exception ex) throws java.io.IOException {
        if (ex instanceof IllegalArgumentException illegalArgumentException) {
            return illegalArgumentException;
        }
        if (ex instanceof java.io.IOException ioException) {
            throw ioException;
        }
        return new IllegalStateException("导入失败: " + resolveRootCauseMessage(ex), ex);
    }

    private String resolveRootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage()) ? current.getMessage() : throwable.getClass().getSimpleName();
    }
}
