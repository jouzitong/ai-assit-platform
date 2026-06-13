package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.entity.DbTableFieldMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.DbTableIndexMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.DbTableMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportResultDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableIndexMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.excel.DbMetaWorkbookTemplateConfig;
import ai.platform.aiassit.db.engine.meta.entity.excel.DbTableFieldMetaExcelRow;
import ai.platform.aiassit.db.engine.meta.entity.excel.DbTableIndexMetaExcelRow;
import ai.platform.aiassit.db.engine.meta.entity.excel.DbTableMetaExcelRow;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableIndexMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbMetaWorkbookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DbMetaWorkbookServiceImpl implements DbMetaWorkbookService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String TEMPLATE_CONFIG_CLASSPATH = "db-meta-workbook-template.json";
    private static final String OPTION_SHEET_NAME = "_options";

    private static final int DATA_VALIDATION_FIRST_ROW = 1;
    private static final int DATA_VALIDATION_LAST_ROW = 2000;

    private final DbTableMetaServiceImpl tableMetaService;
    private final DbTableFieldMetaServiceImpl fieldMetaService;
    private final DbTableIndexMetaServiceImpl indexMetaService;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String templateConfigLocation;

    public DbMetaWorkbookServiceImpl(
            DbTableMetaServiceImpl tableMetaService,
            DbTableFieldMetaServiceImpl fieldMetaService,
            DbTableIndexMetaServiceImpl indexMetaService,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            @Value("${aiassit.db.meta.workbook.template-config-location:}") String templateConfigLocation
    ) {
        this.tableMetaService = tableMetaService;
        this.fieldMetaService = fieldMetaService;
        this.indexMetaService = indexMetaService;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.templateConfigLocation = templateConfigLocation;
    }

    @Override
    public byte[] exportWorkbook(String sourceKey) throws IOException {
        WorkbookTemplateContext templateContext = loadTemplateContext();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet tableSheet = writeTableSheet(workbook, sourceKey, templateContext, SheetMode.EXPORT);
            Sheet fieldSheet = writeFieldSheet(workbook, sourceKey, templateContext, SheetMode.EXPORT);
            Sheet indexSheet = writeIndexSheet(workbook, sourceKey, templateContext, SheetMode.EXPORT);
            configureWorkbookValidations(workbook, tableSheet, fieldSheet, indexSheet, templateContext, SheetMode.EXPORT);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    @Override
    public byte[] exportTemplateWorkbook() throws IOException {
        WorkbookTemplateContext templateContext = loadTemplateContext();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet tableSheet = createSheetTemplate(workbook, templateContext.getRequiredSheetConfig("table", SheetMode.IMPORT));
            Sheet fieldSheet = createSheetTemplate(workbook, templateContext.getRequiredSheetConfig("field", SheetMode.IMPORT));
            Sheet indexSheet = createSheetTemplate(workbook, templateContext.getRequiredSheetConfig("index", SheetMode.IMPORT));
            configureWorkbookValidations(workbook, tableSheet, fieldSheet, indexSheet, templateContext, SheetMode.IMPORT);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DbMetaImportResultDTO importWorkbook(String sourceKey, MultipartFile file) throws IOException {
        WorkbookTemplateContext templateContext = loadTemplateContext();
        String originalFilename = file == null ? null : file.getOriginalFilename();
        long fileSize = file == null ? 0L : file.getSize();
        log.info("开始导入数据库元数据工作簿, sourceKey={}, fileName={}, fileSize={}", sourceKey, originalFilename, fileSize);
        try (InputStream inputStream = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            List<DbTableMetaExcelRow> tableRows = readTableRows(sourceKey, workbook.getSheet(templateContext.resolveSheetName("table")), templateContext);
            List<DbTableFieldMetaExcelRow> fieldRows = readFieldRows(sourceKey, workbook.getSheet(templateContext.resolveSheetName("field")), templateContext);
            List<DbTableIndexMetaExcelRow> indexRows = readIndexRows(sourceKey, workbook.getSheet(templateContext.resolveSheetName("index")), templateContext);
            log.info(
                    "数据库元数据工作簿读取完成, sourceKey={}, tableRows={}, fieldRows={}, indexRows={}",
                    sourceKey, tableRows.size(), fieldRows.size(), indexRows.size()
            );

            int tableCreatedCount = 0;
            int tableUpdatedCount = 0;
            for (DbTableMetaExcelRow row : tableRows) {
                DbTableMetaDTO existing = findExistingTable(row.getSourceKey(), row.getTableName());
                if (existing == null) {
                    tableMetaService.add(toTableDto(row, null));
                    tableCreatedCount++;
                } else {
                    updateExistingTable(existing.getId(), row);
                    tableUpdatedCount++;
                }
            }
            log.info(
                    "数据表元数据导入完成, sourceKey={}, createdCount={}, updatedCount={}",
                    sourceKey, tableCreatedCount, tableUpdatedCount
            );

            int fieldCreatedCount = 0;
            int fieldUpdatedCount = 0;
            for (DbTableFieldMetaExcelRow row : fieldRows) {
                DbTableFieldMetaDTO existing = findExistingField(row.getSourceKey(), row.getTableName(), row.getColumnName());
                if (existing == null) {
                    fieldMetaService.add(toFieldDto(row, null));
                    fieldCreatedCount++;
                } else {
                    updateExistingField(existing.getId(), row);
                    fieldUpdatedCount++;
                }
            }
            log.info("字段元数据导入完成, sourceKey={}, createdCount={}, updatedCount={}",
                    sourceKey, fieldCreatedCount, fieldUpdatedCount);

            int indexCreatedCount = 0;
            int indexUpdatedCount = 0;
            for (DbTableIndexMetaExcelRow row : indexRows) {
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
                    "索引元数据导入完成, sourceKey={}, createdCount={}, updatedCount={}",
                    sourceKey, indexCreatedCount, indexUpdatedCount
            );
            log.info("数据库元数据工作簿导入完成, sourceKey={}, tableCreatedCount={}, tableUpdatedCount={}, " +
                            "fieldCreatedCount={}, fieldUpdatedCount={}, indexCreatedCount={}, indexUpdatedCount={}",
                    sourceKey, tableCreatedCount, tableUpdatedCount, fieldCreatedCount, fieldUpdatedCount, indexCreatedCount, indexUpdatedCount
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
            log.error("数据库元数据工作簿导入失败, sourceKey={}, fileName={}", sourceKey, originalFilename, ex);
            throw wrapImportException(ex);
        }
    }

    private Sheet writeTableSheet(XSSFWorkbook workbook, String sourceKey, WorkbookTemplateContext templateContext, SheetMode sheetMode) {
        DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = templateContext.getRequiredSheetConfig("table", sheetMode);
        Sheet sheet = workbook.createSheet(sheetConfig.getName());
        createHeader(workbook, sheet, sheetConfig);

        List<DbTableMetaDTO> entityList = listTables(sourceKey);
        int rowIndex = 1;
        for (DbTableMetaDTO entity : entityList) {
            Row row = sheet.createRow(rowIndex++);
            writeRow(row, buildTableRowValues(entity, sheetConfig));
        }
        applyColumnLayout(sheet, sheetConfig);
        return sheet;
    }

    private Sheet writeFieldSheet(XSSFWorkbook workbook, String sourceKey, WorkbookTemplateContext templateContext, SheetMode sheetMode) {
        DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = templateContext.getRequiredSheetConfig("field", sheetMode);
        Sheet sheet = workbook.createSheet(sheetConfig.getName());
        createHeader(workbook, sheet, sheetConfig);

        List<DbTableFieldMetaDTO> entityList = listFields(sourceKey);
        int rowIndex = 1;
        for (DbTableFieldMetaDTO entity : entityList) {
            Row row = sheet.createRow(rowIndex++);
            writeRow(row, buildFieldRowValues(entity, sheetConfig));
        }
        applyColumnLayout(sheet, sheetConfig);
        return sheet;
    }

    private Sheet writeIndexSheet(XSSFWorkbook workbook, String sourceKey, WorkbookTemplateContext templateContext, SheetMode sheetMode) {
        DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = templateContext.getRequiredSheetConfig("index", sheetMode);
        Sheet sheet = workbook.createSheet(sheetConfig.getName());
        createHeader(workbook, sheet, sheetConfig);

        List<DbTableIndexMetaDTO> entityList = listIndexes(sourceKey);
        int rowIndex = 1;
        for (DbTableIndexMetaDTO entity : entityList) {
            Row row = sheet.createRow(rowIndex++);
            writeRow(row, buildIndexRowValues(entity, sheetConfig));
        }
        applyColumnLayout(sheet, sheetConfig);
        return sheet;
    }

    private List<DbTableMetaExcelRow> readTableRows(String sourceKey, Sheet sheet, WorkbookTemplateContext templateContext) {
        List<DbTableMetaExcelRow> rowList = new ArrayList<>();
        if (sheet == null) {
            return rowList;
        }
        DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = templateContext.getRequiredSheetConfig("table");
        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> columnIndexMap = buildSheetColumnIndexMap(sheet, sheetConfig, formatter);
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (isBlankRow(row)) {
                continue;
            }
            DbTableMetaExcelRow excelRow = new DbTableMetaExcelRow();
            excelRow.setSourceKey(resolveSourceKey(sourceKey));
            excelRow.setTableName(readRequiredString(row, columnIndexMap.get("tableName"), formatter, sheetConfig.getName(), i, "tableName"));
            excelRow.setTableComment(readString(row, columnIndexMap.get("tableComment"), formatter));
            excelRow.setTableType(readString(row, columnIndexMap.get("tableType"), formatter));
            excelRow.setLayerType(readString(row, columnIndexMap.get("layerType"), formatter));
            excelRow.setRowCount(readLong(row, columnIndexMap.get("rowCount"), formatter));
            excelRow.setColumnCount(readInteger(row, columnIndexMap.get("columnCount"), formatter));
            excelRow.setPartitionKey(readString(row, columnIndexMap.get("partitionKey"), formatter));
            excelRow.setFreshnessSeconds(readInteger(row, columnIndexMap.get("freshnessSeconds"), formatter));
            excelRow.setStatus(readString(row, columnIndexMap.get("status"), formatter));
            excelRow.setEnabled(readBoolean(row, columnIndexMap.get("enabled"), formatter));
            excelRow.setLastScanAt(readString(row, columnIndexMap.get("lastScanAt"), formatter));
            excelRow.setLastSyncAt(readString(row, columnIndexMap.get("lastSyncAt"), formatter));
            excelRow.setRemark(readString(row, columnIndexMap.get("remark"), formatter));
            rowList.add(excelRow);
        }
        return rowList;
    }

    private List<DbTableFieldMetaExcelRow> readFieldRows(String sourceKey, Sheet sheet, WorkbookTemplateContext templateContext) {
        List<DbTableFieldMetaExcelRow> rowList = new ArrayList<>();
        if (sheet == null) {
            return rowList;
        }
        DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = templateContext.getRequiredSheetConfig("field");
        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> columnIndexMap = buildSheetColumnIndexMap(sheet, sheetConfig, formatter);
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (isBlankRow(row)) {
                continue;
            }
            DbTableFieldMetaExcelRow excelRow = new DbTableFieldMetaExcelRow();
            excelRow.setSourceKey(resolveSourceKey(sourceKey));
            excelRow.setTableName(readRequiredString(row, columnIndexMap.get("tableName"), formatter, sheetConfig.getName(), i, "tableName"));
            excelRow.setColumnName(readRequiredString(row, columnIndexMap.get("columnName"), formatter, sheetConfig.getName(), i, "columnName"));
            excelRow.setColumnComment(readString(row, columnIndexMap.get("columnComment"), formatter));
            excelRow.setDataType(readString(row, columnIndexMap.get("dataType"), formatter));
            excelRow.setColumnLength(readInteger(row, columnIndexMap.get("columnLength"), formatter));
            excelRow.setColumnPrecision(readInteger(row, columnIndexMap.get("columnPrecision"), formatter));
            excelRow.setColumnScale(readInteger(row, columnIndexMap.get("columnScale"), formatter));
            excelRow.setNullable(readBoolean(row, columnIndexMap.get("nullable"), formatter));
            excelRow.setPrimaryKey(readBoolean(row, columnIndexMap.get("primaryKey"), formatter));
            excelRow.setPartitionKey(readBoolean(row, columnIndexMap.get("partitionKey"), formatter));
            excelRow.setDefaultValue(readString(row, columnIndexMap.get("defaultValue"), formatter));
            excelRow.setOrdinalPosition(readInteger(row, columnIndexMap.get("ordinalPosition"), formatter));
            excelRow.setFieldRole(readString(row, columnIndexMap.get("fieldRole"), formatter));
            excelRow.setEnabled(readBoolean(row, columnIndexMap.get("enabled"), formatter));
            excelRow.setRemark(readString(row, columnIndexMap.get("remark"), formatter));
            rowList.add(excelRow);
        }
        return rowList;
    }

    private List<DbTableIndexMetaExcelRow> readIndexRows(String sourceKey, Sheet sheet, WorkbookTemplateContext templateContext) {
        List<DbTableIndexMetaExcelRow> rowList = new ArrayList<>();
        if (sheet == null) {
            return rowList;
        }
        DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = templateContext.getRequiredSheetConfig("index");
        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> columnIndexMap = buildSheetColumnIndexMap(sheet, sheetConfig, formatter);
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (isBlankRow(row)) {
                continue;
            }
            DbTableIndexMetaExcelRow excelRow = new DbTableIndexMetaExcelRow();
            excelRow.setSourceKey(resolveSourceKey(sourceKey));
            excelRow.setTableName(readRequiredString(row, columnIndexMap.get("tableName"), formatter, sheetConfig.getName(), i, "tableName"));
            excelRow.setIndexName(readRequiredString(row, columnIndexMap.get("indexName"), formatter, sheetConfig.getName(), i, "indexName"));
            excelRow.setIndexType(readString(row, columnIndexMap.get("indexType"), formatter));
            excelRow.setUniqueFlag(readBoolean(row, columnIndexMap.get("uniqueFlag"), formatter));
            excelRow.setPrimaryFlag(readBoolean(row, columnIndexMap.get("primaryFlag"), formatter));
            excelRow.setColumnName(readRequiredString(row, columnIndexMap.get("columnName"), formatter, sheetConfig.getName(), i, "columnName"));
            excelRow.setColumnOrder(readInteger(row, columnIndexMap.get("columnOrder"), formatter));
            excelRow.setEnabled(readBoolean(row, columnIndexMap.get("enabled"), formatter));
            excelRow.setRemark(readString(row, columnIndexMap.get("remark"), formatter));
            rowList.add(excelRow);
        }
        return rowList;
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

    private List<DbTableMetaDTO> listTables(String sourceKey) {
        DbTableMetaQueryRequest query = new DbTableMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setSize(Integer.MAX_VALUE);
        return tableMetaService.queryAll(query).stream()
                .sorted((left, right) -> compareNullableString(left.getTableName(), right.getTableName()))
                .toList();
    }

    private List<DbTableFieldMetaDTO> listFields(String sourceKey) {
        DbTableFieldMetaQueryRequest query = new DbTableFieldMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setSize(Integer.MAX_VALUE);
        return fieldMetaService.queryAll(query).stream()
                .sorted((left, right) -> {
                    int tableCompare = compareNullableString(left.getTableName(), right.getTableName());
                    if (tableCompare != 0) {
                        return tableCompare;
                    }
                    return compareNullableInteger(left.getOrdinalPosition(), right.getOrdinalPosition());
                })
                .toList();
    }

    private List<DbTableIndexMetaDTO> listIndexes(String sourceKey) {
        DbTableIndexMetaQueryRequest query = new DbTableIndexMetaQueryRequest();
        query.setSourceKey(sourceKey);
        query.setSize(Integer.MAX_VALUE);
        return indexMetaService.queryAll(query).stream()
                .sorted((left, right) -> {
                    int tableCompare = compareNullableString(left.getTableName(), right.getTableName());
                    if (tableCompare != 0) {
                        return tableCompare;
                    }
                    int indexCompare = compareNullableString(left.getIndexName(), right.getIndexName());
                    if (indexCompare != 0) {
                        return indexCompare;
                    }
                    return compareNullableInteger(left.getColumnOrder(), right.getColumnOrder());
                })
                .toList();
    }

    private DbTableMetaDTO toTableDto(DbTableMetaExcelRow row, DbTableMetaDTO existing) {
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

    private void updateExistingTable(Long id, DbTableMetaExcelRow row) {
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

    private DbTableFieldMetaDTO toFieldDto(DbTableFieldMetaExcelRow row, DbTableFieldMetaDTO existing) {
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

    private void updateExistingField(Long id, DbTableFieldMetaExcelRow row) {
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

    private DbTableIndexMetaDTO toIndexDto(DbTableIndexMetaExcelRow row, DbTableIndexMetaDTO existing) {
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

    private void updateExistingIndex(Long id, DbTableIndexMetaExcelRow row) {
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

    private Sheet createSheetTemplate(XSSFWorkbook workbook, DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig) {
        Sheet sheet = workbook.createSheet(sheetConfig.getName());
        createHeader(workbook, sheet, sheetConfig);
        applyColumnLayout(sheet, sheetConfig);
        return sheet;
    }

    private void createHeader(XSSFWorkbook workbook, Sheet sheet, DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig) {
        Row row = sheet.createRow(0);
        CreationHelper creationHelper = workbook.getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        Map<String, CellStyle> headerStyles = new HashMap<>();
        for (int i = 0; i < sheetConfig.getColumns().size(); i++) {
            DbMetaWorkbookTemplateConfig.ColumnConfig columnConfig = sheetConfig.getColumns().get(i);
            Cell cell = row.createCell(i);
            cell.setCellValue(columnConfig.getLabel());
            cell.setCellStyle(resolveHeaderStyle(workbook, headerStyles, columnConfig.getHeaderColor()));
            attachHeaderCommentIfPresent(creationHelper, drawing, cell, columnConfig);
        }
    }

    private void writeRow(Row row, Object... values) {
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            Object value = values[i];
            if (value == null) {
                cell.setBlank();
            } else if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else if (value instanceof Boolean bool) {
                cell.setCellValue(bool);
            } else {
                cell.setCellValue(String.valueOf(value));
            }
        }
    }

    private Object[] buildTableRowValues(DbTableMetaDTO entity, DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig) {
        List<Object> values = new ArrayList<>();
        for (DbMetaWorkbookTemplateConfig.ColumnConfig columnConfig : sheetConfig.getColumns()) {
            values.add(resolveTableColumnValue(entity, columnConfig.getKey()));
        }
        return values.toArray();
    }

    private Object resolveTableColumnValue(DbTableMetaDTO entity, String columnKey) {
        return switch (columnKey) {
            case "tableName" -> entity.getTableName();
            case "tableComment" -> entity.getTableComment();
            case "tableType" -> entity.getTableType();
            case "layerType" -> entity.getLayerType();
            case "rowCount" -> entity.getRowCount();
            case "columnCount" -> entity.getColumnCount();
            case "partitionKey" -> entity.getPartitionKey();
            case "freshnessSeconds" -> entity.getFreshnessSeconds();
            case "status" -> entity.getStatus();
            case "enabled" -> entity.getEnabled();
            case "lastScanAt" -> formatDateTime(entity.getLastScanAt());
            case "lastSyncAt" -> formatDateTime(entity.getLastSyncAt());
            case "remark" -> entity.getRemark();
            default -> null;
        };
    }

    private Object[] buildFieldRowValues(DbTableFieldMetaDTO entity, DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig) {
        List<Object> values = new ArrayList<>();
        for (DbMetaWorkbookTemplateConfig.ColumnConfig columnConfig : sheetConfig.getColumns()) {
            values.add(resolveFieldColumnValue(entity, columnConfig.getKey()));
        }
        return values.toArray();
    }

    private Object resolveFieldColumnValue(DbTableFieldMetaDTO entity, String columnKey) {
        return switch (columnKey) {
            case "tableName" -> entity.getTableName();
            case "columnName" -> entity.getColumnName();
            case "columnComment" -> entity.getColumnComment();
            case "dataType" -> entity.getDataType();
            case "columnLength" -> entity.getColumnLength();
            case "columnPrecision" -> entity.getColumnPrecision();
            case "columnScale" -> entity.getColumnScale();
            case "nullable" -> entity.getNullable();
            case "primaryKey" -> entity.getPrimaryKey();
            case "partitionKey" -> entity.getPartitionKey();
            case "defaultValue" -> entity.getDefaultValue();
            case "ordinalPosition" -> entity.getOrdinalPosition();
            case "fieldRole" -> entity.getFieldRole();
            case "enabled" -> entity.getEnabled();
            case "remark" -> entity.getRemark();
            default -> null;
        };
    }

    private Object[] buildIndexRowValues(DbTableIndexMetaDTO entity, DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig) {
        List<Object> values = new ArrayList<>();
        for (DbMetaWorkbookTemplateConfig.ColumnConfig columnConfig : sheetConfig.getColumns()) {
            values.add(resolveIndexColumnValue(entity, columnConfig.getKey()));
        }
        return values.toArray();
    }

    private Object resolveIndexColumnValue(DbTableIndexMetaDTO entity, String columnKey) {
        return switch (columnKey) {
            case "tableName" -> entity.getTableName();
            case "indexName" -> entity.getIndexName();
            case "indexType" -> entity.getIndexType();
            case "uniqueFlag" -> entity.getUniqueFlag();
            case "primaryFlag" -> entity.getPrimaryFlag();
            case "columnName" -> entity.getColumnName();
            case "columnOrder" -> entity.getColumnOrder();
            case "enabled" -> entity.getEnabled();
            case "remark" -> entity.getRemark();
            default -> null;
        };
    }

    private void applyColumnLayout(Sheet sheet, DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig) {
        for (int i = 0; i < sheetConfig.getColumns().size(); i++) {
            DbMetaWorkbookTemplateConfig.ColumnConfig columnConfig = sheetConfig.getColumns().get(i);
            if (columnConfig.getWidth() != null && columnConfig.getWidth() > 0) {
                sheet.setColumnWidth(i, Math.min(columnConfig.getWidth(), 80) * 256);
                continue;
            }
            sheet.autoSizeColumn(i);
            int width = Math.min(sheet.getColumnWidth(i) + 1024, 40 * 256);
            sheet.setColumnWidth(i, width);
        }
    }

    private void attachHeaderCommentIfPresent(
            CreationHelper creationHelper,
            Drawing<?> drawing,
            Cell cell,
            DbMetaWorkbookTemplateConfig.ColumnConfig columnConfig
    ) {
        List<String> lines = new ArrayList<>();
        if (Boolean.TRUE.equals(columnConfig.getRequired())) {
            lines.add("必填");
        }
        if (StringUtils.hasText(columnConfig.getDefaultValue())) {
            lines.add("默认值: " + columnConfig.getDefaultValue());
        }
        if (!CollectionUtils.isEmpty(columnConfig.getMasks())) {
            lines.add("枚举: " + String.join(", ", columnConfig.getMasks()));
        }
        if (StringUtils.hasText(columnConfig.getFormat())) {
            lines.add("格式: " + columnConfig.getFormat());
        }
        if (StringUtils.hasText(columnConfig.getDescription())) {
            lines.add(columnConfig.getDescription());
        }
        if (lines.isEmpty()) {
            return;
        }
        XSSFClientAnchor anchor = new XSSFClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setCol2(cell.getColumnIndex() + 2);
        anchor.setRow1(cell.getRowIndex());
        anchor.setRow2(cell.getRowIndex() + 3);
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(creationHelper.createRichTextString(String.join("\n", lines)));
        comment.setAddress(cell.getAddress());
        cell.setCellComment(comment);
    }

    private CellStyle resolveHeaderStyle(XSSFWorkbook workbook, Map<String, CellStyle> headerStyles, String headerColor) {
        String styleKey = StringUtils.hasText(headerColor) ? headerColor.trim() : "_default";
        CellStyle existingStyle = headerStyles.get(styleKey);
        if (existingStyle != null) {
            return existingStyle;
        }
        XSSFCellStyle style = workbook.createCellStyle();
        if (StringUtils.hasText(headerColor)) {
            style.setFillForegroundColor(new XSSFColor(Color.decode(headerColor.trim()), null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        headerStyles.put(styleKey, style);
        return style;
    }

    private boolean isBlankRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && StringUtils.hasText(new DataFormatter().formatCellValue(cell))) {
                return false;
            }
        }
        return true;
    }

    private String readRequiredString(Row row, Integer index, DataFormatter formatter, String sheetName, int rowIndex, String fieldName) {
        if (index == null) {
            throw new IllegalArgumentException("sheet[" + sheetName + "] 缺少列 " + fieldName);
        }
        String value = readString(row, index, formatter);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("sheet[" + sheetName + "] 第 " + (rowIndex + 1) + " 行缺少必填字段 " + fieldName);
        }
        return value;
    }

    private String readString(Row row, Integer index, DataFormatter formatter) {
        if (row == null || index == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Integer readInteger(Row row, Integer index, DataFormatter formatter) {
        String value = readString(row, index, formatter);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private Long readLong(Row row, Integer index, DataFormatter formatter) {
        String value = readString(row, index, formatter);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Long.parseLong(value);
    }

    private Boolean readBoolean(Row row, Integer index, DataFormatter formatter) {
        String value = readString(row, index, formatter);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized) || "是".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized) || "否".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("布尔值解析失败: " + value);
    }

    private String resolveSourceKey(String requestSourceKey) {
        if (!StringUtils.hasText(requestSourceKey)) {
            throw new IllegalArgumentException("缺少 sourceKey");
        }
        return requestSourceKey;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
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

    private void configureWorkbookValidations(
            XSSFWorkbook workbook,
            Sheet tableSheet,
            Sheet fieldSheet,
            Sheet indexSheet,
            WorkbookTemplateContext templateContext,
            SheetMode sheetMode
    ) {
        Map<String, Sheet> sheetByKey = Map.of(
                "table", tableSheet,
                "field", fieldSheet,
                "index", indexSheet
        );
        Sheet optionSheet = workbook.createSheet(OPTION_SHEET_NAME);
        workbook.setSheetHidden(workbook.getSheetIndex(optionSheet), true);

        int optionColumnIndex = 0;
        for (DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig : templateContext.getSheetConfigs(sheetMode)) {
            Sheet sheet = sheetByKey.get(sheetConfig.getKey());
            if (sheet == null) {
                continue;
            }
            for (DbMetaWorkbookTemplateConfig.ColumnConfig columnConfig : sheetConfig.getColumns()) {
                if (!CollectionUtils.isEmpty(columnConfig.getMasks())) {
                    String maskRangeName = buildMaskRangeName(sheetConfig.getKey(), columnConfig.getKey());
                    writeOptionColumn(optionSheet, optionColumnIndex, columnConfig.getMasks());
                    createNamedRange(workbook, maskRangeName, OPTION_SHEET_NAME, optionColumnIndex, 1, columnConfig.getMasks().size());
                    addNamedRangeValidation(sheet, maskRangeName, getColumnIndex(sheetConfig, columnConfig.getKey()));
                    optionColumnIndex++;
                }
                if (StringUtils.hasText(columnConfig.getNamedRange())) {
                    addNamedRangeValidation(sheet, columnConfig.getNamedRange(), getColumnIndex(sheetConfig, columnConfig.getKey()));
                }
            }
        }

        if (!CollectionUtils.isEmpty(templateContext.getTemplateConfig().getNamedRanges())) {
            for (DbMetaWorkbookTemplateConfig.NamedRangeConfig namedRangeConfig : templateContext.getTemplateConfig().getNamedRanges()) {
                DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = templateContext.getOptionalSheetConfig(namedRangeConfig.getSheetKey(), sheetMode);
                if (sheetConfig == null || !hasColumn(sheetConfig, namedRangeConfig.getColumnKey())) {
                    continue;
                }
                createNamedRange(
                        workbook,
                        namedRangeConfig.getName(),
                        sheetConfig.getName(),
                        getColumnIndex(sheetConfig, namedRangeConfig.getColumnKey()),
                        namedRangeConfig.getStartRow(),
                        namedRangeConfig.getEndRow()
                );
            }
        }
    }

    private void writeOptionColumn(Sheet optionSheet, int columnIndex, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            Row row = optionSheet.getRow(i);
            if (row == null) {
                row = optionSheet.createRow(i);
            }
            row.createCell(columnIndex).setCellValue(values.get(i));
        }
    }

    private void validateTemplateConfig(DbMetaWorkbookTemplateConfig config) {
        if (config == null || CollectionUtils.isEmpty(config.getSheets())) {
            throw new IllegalStateException("工作簿模板配置缺少 sheets");
        }
        for (DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig : config.getSheets()) {
            if (!StringUtils.hasText(sheetConfig.getKey()) || !StringUtils.hasText(sheetConfig.getName())) {
                throw new IllegalStateException("工作簿模板 sheet 缺少 key 或 name");
            }
            if (CollectionUtils.isEmpty(sheetConfig.getColumns())) {
                throw new IllegalStateException("工作簿模板 sheet[" + sheetConfig.getKey() + "] 缺少 columns");
            }
        }
    }

    private WorkbookTemplateContext loadTemplateContext() {
        DbMetaWorkbookTemplateConfig config = loadTemplateConfig();
        return new WorkbookTemplateContext(config, buildSheetConfigMap(config));
    }

    private DbMetaWorkbookTemplateConfig loadTemplateConfig() {
        Resource resource = resolveTemplateConfigResource();
        try (InputStream inputStream = resource.getInputStream()) {
            DbMetaWorkbookTemplateConfig config = objectMapper.readValue(inputStream, DbMetaWorkbookTemplateConfig.class);
            validateTemplateConfig(config);
            return config;
        } catch (IOException ex) {
            throw new IllegalStateException("加载工作簿模板配置失败: " + resource.getDescription(), ex);
        }
    }

    private Resource resolveTemplateConfigResource() {
        if (StringUtils.hasText(templateConfigLocation)) {
            Resource externalResource = resourceLoader.getResource(templateConfigLocation);
            if (externalResource.exists()) {
                return externalResource;
            }
            throw new IllegalStateException("工作簿模板配置不存在: " + templateConfigLocation);
        }
        return resourceLoader.getResource("classpath:" + TEMPLATE_CONFIG_CLASSPATH);
    }

    private Map<String, DbMetaWorkbookTemplateConfig.SheetConfig> buildSheetConfigMap(DbMetaWorkbookTemplateConfig config) {
        return config.getSheets().stream()
                .collect(Collectors.toMap(
                        DbMetaWorkbookTemplateConfig.SheetConfig::getKey,
                        sheetConfig -> sheetConfig,
                        (left, right) -> left,
                        HashMap::new
                ));
    }

    private Map<String, Integer> buildColumnIndexMap(DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig) {
        Map<String, Integer> columnIndexMap = new HashMap<>();
        for (int i = 0; i < sheetConfig.getColumns().size(); i++) {
            columnIndexMap.put(sheetConfig.getColumns().get(i).getKey(), i);
        }
        return columnIndexMap;
    }

    private Map<String, Integer> buildSheetColumnIndexMap(
            Sheet sheet,
            DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig,
            DataFormatter formatter
    ) {
        Map<String, Integer> sheetColumnIndexMap = new HashMap<>();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return sheetColumnIndexMap;
        }
        Map<String, Integer> headerLabelIndexMap = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String headerLabel = readString(headerRow, i, formatter);
            if (StringUtils.hasText(headerLabel)) {
                headerLabelIndexMap.put(headerLabel, i);
            }
        }
        for (DbMetaWorkbookTemplateConfig.ColumnConfig columnConfig : sheetConfig.getColumns()) {
            Integer columnIndex = headerLabelIndexMap.get(columnConfig.getLabel());
            if (columnIndex != null) {
                sheetColumnIndexMap.put(columnConfig.getKey(), columnIndex);
            }
        }
        return sheetColumnIndexMap;
    }

    private int getColumnIndex(DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig, String columnKey) {
        return requiredColumnIndex(buildColumnIndexMap(sheetConfig), columnKey);
    }

    private int requiredColumnIndex(Map<String, Integer> columnIndexMap, String columnKey) {
        Integer columnIndex = columnIndexMap.get(columnKey);
        if (columnIndex == null) {
            throw new IllegalStateException("工作簿模板缺少列配置: " + columnKey);
        }
        return columnIndex;
    }

    private String buildMaskRangeName(String sheetKey, String columnKey) {
        return sheetKey + "_" + columnKey + "_masks";
    }

    private boolean hasColumn(DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig, String columnKey) {
        return sheetConfig.getColumns().stream().anyMatch(columnConfig -> columnKey.equals(columnConfig.getKey()));
    }

    private int compareNullableString(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private int compareNullableInteger(Integer left, Integer right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private void createNamedRange(
            XSSFWorkbook workbook,
            String rangeName,
            String sheetName,
            int columnIndex,
            int startRow,
            int endRow
    ) {
        if (!StringUtils.hasText(rangeName)) {
            return;
        }
        Name namedRange = workbook.createName();
        namedRange.setNameName(rangeName);
        String columnLetter = toColumnLetter(columnIndex);
        namedRange.setRefersToFormula("'" + sheetName + "'!$" + columnLetter + "$" + startRow + ":$" + columnLetter + "$" + endRow);
    }

    private void addNamedRangeValidation(Sheet sheet, String rangeName, int columnIndex) {
        if (!StringUtils.hasText(rangeName)) {
            return;
        }
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createFormulaListConstraint(rangeName);
        CellRangeAddressList addressList = new CellRangeAddressList(
                DATA_VALIDATION_FIRST_ROW,
                DATA_VALIDATION_LAST_ROW,
                columnIndex,
                columnIndex
        );
        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setSuppressDropDownArrow(false);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    private String toColumnLetter(int columnIndex) {
        int value = columnIndex;
        StringBuilder builder = new StringBuilder();
        while (value >= 0) {
            builder.insert(0, (char) ('A' + (value % 26)));
            value = value / 26 - 1;
        }
        return builder.toString();
    }

    private RuntimeException wrapImportException(Exception ex) throws IOException {
        if (ex instanceof IllegalArgumentException illegalArgumentException) {
            return illegalArgumentException;
        }
        if (ex instanceof IOException ioException) {
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

    private DbMetaWorkbookTemplateConfig.SheetConfig filterSheetConfig(
            DbMetaWorkbookTemplateConfig.SheetConfig source,
            SheetMode sheetMode
    ) {
        DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = new DbMetaWorkbookTemplateConfig.SheetConfig();
        sheetConfig.setKey(source.getKey());
        sheetConfig.setName(source.getName());
        List<DbMetaWorkbookTemplateConfig.ColumnConfig> columns = source.getColumns().stream()
                .filter(columnConfig -> isColumnVisible(columnConfig, sheetMode))
                .collect(Collectors.toList());
        sheetConfig.setColumns(columns);
        return sheetConfig;
    }

    private boolean isColumnVisible(DbMetaWorkbookTemplateConfig.ColumnConfig columnConfig, SheetMode sheetMode) {
        if (sheetMode == SheetMode.IMPORT) {
            return !Boolean.FALSE.equals(columnConfig.getImportable());
        }
        return !Boolean.FALSE.equals(columnConfig.getExportable());
    }

    private enum SheetMode {
        IMPORT,
        EXPORT
    }

    private static final class WorkbookTemplateContext {

        private final DbMetaWorkbookTemplateConfig templateConfig;
        private final Map<String, DbMetaWorkbookTemplateConfig.SheetConfig> sheetConfigByKey;

        private WorkbookTemplateContext(
                DbMetaWorkbookTemplateConfig templateConfig,
                Map<String, DbMetaWorkbookTemplateConfig.SheetConfig> sheetConfigByKey
        ) {
            this.templateConfig = templateConfig;
            this.sheetConfigByKey = sheetConfigByKey;
        }

        private DbMetaWorkbookTemplateConfig getTemplateConfig() {
            return templateConfig;
        }

        private DbMetaWorkbookTemplateConfig.SheetConfig getRequiredSheetConfig(String sheetKey) {
            DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = sheetConfigByKey.get(sheetKey);
            if (sheetConfig == null) {
                throw new IllegalStateException("工作簿模板缺少 sheet: " + sheetKey);
            }
            return sheetConfig;
        }

        private String resolveSheetName(String sheetKey) {
            return getRequiredSheetConfig(sheetKey).getName();
        }

        private DbMetaWorkbookTemplateConfig.SheetConfig getRequiredSheetConfig(String sheetKey, SheetMode sheetMode) {
            DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = getOptionalSheetConfig(sheetKey, sheetMode);
            if (sheetConfig == null) {
                throw new IllegalStateException("工作簿模板缺少 sheet: " + sheetKey);
            }
            return sheetConfig;
        }

        private DbMetaWorkbookTemplateConfig.SheetConfig getOptionalSheetConfig(String sheetKey, SheetMode sheetMode) {
            DbMetaWorkbookTemplateConfig.SheetConfig source = sheetConfigByKey.get(sheetKey);
            if (source == null) {
                return null;
            }
            DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = new DbMetaWorkbookTemplateConfig.SheetConfig();
            sheetConfig.setKey(source.getKey());
            sheetConfig.setName(source.getName());
            List<DbMetaWorkbookTemplateConfig.ColumnConfig> columns = source.getColumns().stream()
                    .filter(columnConfig -> sheetMode == SheetMode.IMPORT
                            ? !Boolean.FALSE.equals(columnConfig.getImportable())
                            : !Boolean.FALSE.equals(columnConfig.getExportable()))
                    .collect(Collectors.toList());
            sheetConfig.setColumns(columns);
            return sheetConfig;
        }

        private List<DbMetaWorkbookTemplateConfig.SheetConfig> getSheetConfigs(SheetMode sheetMode) {
            return templateConfig.getSheets().stream()
                    .map(sheetConfig -> getOptionalSheetConfig(sheetConfig.getKey(), sheetMode))
                    .filter(sheetConfig -> sheetConfig != null && !CollectionUtils.isEmpty(sheetConfig.getColumns()))
                    .collect(Collectors.toList());
        }
    }
}
