package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.api.constant.DbEngineBizCodeConstant;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportResultDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableIndexMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.importer.DbMetaImportData;
import ai.platform.aiassit.db.engine.meta.enums.DbMetaImportJobStage;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableIndexMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaQueryRequest;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        return importData(requestSourceKey, file, format, importData, DbMetaImportProgressListener.NOOP);
    }

    @Transactional(rollbackFor = Exception.class)
    public DbMetaImportResultDTO importData(
            String requestSourceKey,
            MultipartFile file,
            String format,
            DbMetaImportData importData,
            DbMetaImportProgressListener progressListener
    ) throws java.io.IOException {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        long fileSize = file == null ? 0L : file.getSize();
        String sourceKey = resolveSourceKey(requestSourceKey);
        log.info("开始导入数据库元数据, format={}, sourceKey={}, fileName={}, fileSize={}", format, sourceKey, originalFilename, fileSize);
        try {
            int tableCreatedCount = 0;
            int tableUpdatedCount = 0;
            List<DbMetaImportData.TableRow> tableRows = importData.getTables();
            List<DbMetaImportData.FieldRow> fieldRows = importData.getFields();
            List<DbMetaImportData.IndexRow> indexRows = importData.getIndexes();
            progressListener.onStageChanged(DbMetaImportJobStage.IMPORTING_TABLES, "开始导入表元数据");
            for (int i = 0; i < tableRows.size(); i++) {
                DbMetaImportData.TableRow row = tableRows.get(i);
                DbTableMetaDTO existing = findExistingTable(sourceKey, row.getTableName());
                if (existing == null) {
                    tableMetaService.add(toTableDto(sourceKey, row, null));
                    tableCreatedCount++;
                } else {
                    updateExistingTable(sourceKey, existing, row);
                    tableUpdatedCount++;
                }
                progressListener.onTableProgress(i + 1, tableRows.size(), tableCreatedCount, tableUpdatedCount);
            }

            int fieldCreatedCount = 0;
            int fieldUpdatedCount = 0;
            progressListener.onStageChanged(DbMetaImportJobStage.IMPORTING_FIELDS, "开始导入字段元数据");
            for (int i = 0; i < fieldRows.size(); i++) {
                DbMetaImportData.FieldRow row = fieldRows.get(i);
                DbTableFieldMetaDTO existing = findExistingField(sourceKey, row.getTableName(), row.getColumnName());
                if (existing == null) {
                    fieldMetaService.add(toFieldDto(sourceKey, row, null));
                    fieldCreatedCount++;
                } else {
                    updateExistingField(sourceKey, existing, row);
                    fieldUpdatedCount++;
                }
                progressListener.onFieldProgress(i + 1, fieldRows.size(), fieldCreatedCount, fieldUpdatedCount);
            }

            int indexCreatedCount = 0;
            int indexUpdatedCount = 0;
            progressListener.onStageChanged(DbMetaImportJobStage.IMPORTING_INDEXES, "开始导入索引元数据");
            for (int i = 0; i < indexRows.size(); i++) {
                DbMetaImportData.IndexRow row = indexRows.get(i);
                DbTableIndexMetaDTO existing = findExistingIndex(
                        sourceKey,
                        row.getTableName(),
                        row.getIndexName(),
                        row.getColumnName()
                );
                if (existing == null) {
                    indexMetaService.add(toIndexDto(sourceKey, row, null));
                    indexCreatedCount++;
                } else {
                    updateExistingIndex(sourceKey, existing, row);
                    indexUpdatedCount++;
                }
                progressListener.onIndexProgress(i + 1, indexRows.size(), indexCreatedCount, indexUpdatedCount);
            }

            progressListener.onStageChanged(DbMetaImportJobStage.FINALIZING, "正在回写表字段数");
            refreshImportedTableColumnCounts(sourceKey, fieldRows);

            log.info(
                    "数据库元数据导入完成, format={}, sourceKey={}, tableCreatedCount={}, tableUpdatedCount={}, fieldCreatedCount={}, fieldUpdatedCount={}, indexCreatedCount={}, indexUpdatedCount={}",
                    format,
                    sourceKey,
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
            log.error("数据库元数据导入失败, format={}, sourceKey={}, fileName={}", format, sourceKey, originalFilename, ex);
            throw wrapImportException(ex);
        }
    }

    private void refreshImportedTableColumnCounts(String sourceKey, List<DbMetaImportData.FieldRow> fieldRows) {
        Set<String> importedTableNames = new LinkedHashSet<>();
        for (DbMetaImportData.FieldRow row : fieldRows) {
            if (row != null && StringUtils.hasText(row.getTableName())) {
                importedTableNames.add(row.getTableName());
            }
        }
        for (String tableName : importedTableNames) {
            DbTableFieldMetaQueryRequest fieldQuery = new DbTableFieldMetaQueryRequest();
            fieldQuery.setSourceKey(sourceKey);
            fieldQuery.setTableName(tableName);
            int columnCount = fieldMetaService.queryAll(fieldQuery).size();
            DbTableMetaDTO existing = findExistingTable(sourceKey, tableName);
            if (existing == null) {
                continue;
            }
            existing.setColumnCount(columnCount);
            DbTableMetaDTO updated = tableMetaService.update(existing.getId(), existing);
            if (updated == null) {
                throw BizException.of(DbEngineBizCodeConstant.DB_META_UPDATE_FAILED, tableName);
            }
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

    private DbTableMetaDTO toTableDto(String sourceKey, DbMetaImportData.TableRow row, DbTableMetaDTO existing) {
        DbTableMetaDTO dto = existing == null ? new DbTableMetaDTO() : existing;
        dto.setSourceKey(sourceKey);
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

    private void updateExistingTable(String sourceKey, DbTableMetaDTO existing, DbMetaImportData.TableRow row) {
        DbTableMetaDTO updated = tableMetaService.update(existing.getId(), toTableDto(sourceKey, row, existing));
        if (updated == null) {
            throw BizException.of(DbEngineBizCodeConstant.DB_META_UPDATE_FAILED, row.getTableName());
        }
    }

    private DbTableFieldMetaDTO toFieldDto(String sourceKey, DbMetaImportData.FieldRow row, DbTableFieldMetaDTO existing) {
        DbTableFieldMetaDTO dto = existing == null ? new DbTableFieldMetaDTO() : existing;
        dto.setSourceKey(sourceKey);
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

    private void updateExistingField(String sourceKey, DbTableFieldMetaDTO existing, DbMetaImportData.FieldRow row) {
        DbTableFieldMetaDTO updated = fieldMetaService.update(existing.getId(), toFieldDto(sourceKey, row, existing));
        if (updated == null) {
            throw BizException.of(DbEngineBizCodeConstant.DB_META_UPDATE_FAILED, row.getColumnName());
        }
    }

    private DbTableIndexMetaDTO toIndexDto(String sourceKey, DbMetaImportData.IndexRow row, DbTableIndexMetaDTO existing) {
        DbTableIndexMetaDTO dto = existing == null ? new DbTableIndexMetaDTO() : existing;
        dto.setSourceKey(sourceKey);
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

    private void updateExistingIndex(String sourceKey, DbTableIndexMetaDTO existing, DbMetaImportData.IndexRow row) {
        DbTableIndexMetaDTO updated = indexMetaService.update(existing.getId(), toIndexDto(sourceKey, row, existing));
        if (updated == null) {
            throw BizException.of(DbEngineBizCodeConstant.DB_META_UPDATE_FAILED, row.getIndexName());
        }
    }

    private String resolveSourceKey(String requestSourceKey) {
        if (StringUtils.hasText(requestSourceKey)) {
            return requestSourceKey.trim();
        }
        throw BizException.illegalParam(DbEngineBizCodeConstant.REQUIRED_SOURCE_KEY);
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
        if (ex instanceof BizException bizException) {
            return bizException;
        }
        if (ex instanceof java.io.IOException ioException) {
            throw ioException;
        }
        return new BizException(ex);
    }

    private String resolveRootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage()) ? current.getMessage() : throwable.getClass().getSimpleName();
    }
}
