package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.entity.DbTableFieldMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.DbTableIndexMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.DbTableMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportResultDTO;
import ai.platform.aiassit.db.engine.meta.entity.excel.DbMetaWorkbookTemplateConfig;
import ai.platform.aiassit.db.engine.meta.entity.excel.DbTableFieldMetaExcelRow;
import ai.platform.aiassit.db.engine.meta.entity.excel.DbTableIndexMetaExcelRow;
import ai.platform.aiassit.db.engine.meta.entity.excel.DbTableMetaExcelRow;
import ai.platform.aiassit.db.engine.meta.mapper.DbTableFieldMetaMapper;
import ai.platform.aiassit.db.engine.meta.mapper.DbTableIndexMetaMapper;
import ai.platform.aiassit.db.engine.meta.mapper.DbTableMetaMapper;
import ai.platform.aiassit.db.engine.meta.service.DbMetaWorkbookService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DbMetaWorkbookServiceImpl implements DbMetaWorkbookService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String TEMPLATE_CONFIG_CLASSPATH = "db-meta-workbook-template.json";
    private static final String OPTION_SHEET_NAME = "_options";

    private static final int DATA_VALIDATION_FIRST_ROW = 1;
    private static final int DATA_VALIDATION_LAST_ROW = 2000;

    private final DbTableMetaMapper tableMetaMapper;
    private final DbTableFieldMetaMapper fieldMetaMapper;
    private final DbTableIndexMetaMapper indexMetaMapper;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String templateConfigLocation;

    public DbMetaWorkbookServiceImpl(
            DbTableMetaMapper tableMetaMapper,
            DbTableFieldMetaMapper fieldMetaMapper,
            DbTableIndexMetaMapper indexMetaMapper,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            @Value("${aiassit.db.meta.workbook.template-config-location:}") String templateConfigLocation
    ) {
        this.tableMetaMapper = tableMetaMapper;
        this.fieldMetaMapper = fieldMetaMapper;
        this.indexMetaMapper = indexMetaMapper;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.templateConfigLocation = templateConfigLocation;
    }

    @Override
    public byte[] exportWorkbook(String sourceKey) throws IOException {
        WorkbookTemplateContext templateContext = loadTemplateContext();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet tableSheet = writeTableSheet(workbook, sourceKey, templateContext);
            Sheet fieldSheet = writeFieldSheet(workbook, sourceKey, templateContext);
            Sheet indexSheet = writeIndexSheet(workbook, sourceKey, templateContext);
            configureWorkbookValidations(workbook, tableSheet, fieldSheet, indexSheet, templateContext);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    @Override
    public byte[] exportTemplateWorkbook() throws IOException {
        WorkbookTemplateContext templateContext = loadTemplateContext();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet tableSheet = createSheetTemplate(workbook, templateContext.getRequiredSheetConfig("table"));
            Sheet fieldSheet = createSheetTemplate(workbook, templateContext.getRequiredSheetConfig("field"));
            Sheet indexSheet = createSheetTemplate(workbook, templateContext.getRequiredSheetConfig("index"));
            configureWorkbookValidations(workbook, tableSheet, fieldSheet, indexSheet, templateContext);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    @Override
    public DbMetaImportResultDTO importWorkbook(String sourceKey, MultipartFile file) throws IOException {
        WorkbookTemplateContext templateContext = loadTemplateContext();
        try (InputStream inputStream = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            List<DbTableMetaExcelRow> tableRows = readTableRows(sourceKey, workbook.getSheet(templateContext.resolveSheetName("table")), templateContext);
            List<DbTableFieldMetaExcelRow> fieldRows = readFieldRows(sourceKey, workbook.getSheet(templateContext.resolveSheetName("field")), templateContext);
            List<DbTableIndexMetaExcelRow> indexRows = readIndexRows(sourceKey, workbook.getSheet(templateContext.resolveSheetName("index")), templateContext);

            int tableCreatedCount = 0;
            int tableUpdatedCount = 0;
            for (DbTableMetaExcelRow row : tableRows) {
                DbTableMetaEntity existing = findExistingTable(row.getSourceKey(), row.getTableName());
                if (existing == null) {
                    tableMetaMapper.insert(toTableEntity(row, null));
                    tableCreatedCount++;
                } else {
                    tableMetaMapper.updateById(toTableEntity(row, existing));
                    tableUpdatedCount++;
                }
            }

            int fieldCreatedCount = 0;
            int fieldUpdatedCount = 0;
            for (DbTableFieldMetaExcelRow row : fieldRows) {
                DbTableFieldMetaEntity existing = findExistingField(row.getSourceKey(), row.getTableName(), row.getColumnName());
                if (existing == null) {
                    fieldMetaMapper.insert(toFieldEntity(row, null));
                    fieldCreatedCount++;
                } else {
                    fieldMetaMapper.updateById(toFieldEntity(row, existing));
                    fieldUpdatedCount++;
                }
            }

            int indexCreatedCount = 0;
            int indexUpdatedCount = 0;
            for (DbTableIndexMetaExcelRow row : indexRows) {
                DbTableIndexMetaEntity existing = findExistingIndex(
                        row.getSourceKey(),
                        row.getTableName(),
                        row.getIndexName(),
                        row.getColumnName()
                );
                if (existing == null) {
                    indexMetaMapper.insert(toIndexEntity(row, null));
                    indexCreatedCount++;
                } else {
                    indexMetaMapper.updateById(toIndexEntity(row, existing));
                    indexUpdatedCount++;
                }
            }

            return DbMetaImportResultDTO.builder()
                    .tableCreatedCount(tableCreatedCount)
                    .tableUpdatedCount(tableUpdatedCount)
                    .fieldCreatedCount(fieldCreatedCount)
                    .fieldUpdatedCount(fieldUpdatedCount)
                    .indexCreatedCount(indexCreatedCount)
                    .indexUpdatedCount(indexUpdatedCount)
                    .build();
        }
    }

    private Sheet writeTableSheet(XSSFWorkbook workbook, String sourceKey, WorkbookTemplateContext templateContext) {
        DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = templateContext.getRequiredSheetConfig("table");
        Sheet sheet = workbook.createSheet(sheetConfig.getName());
        createHeader(workbook, sheet, sheetConfig);

        List<DbTableMetaEntity> entityList = tableMetaMapper.selectList(
                new QueryWrapper<DbTableMetaEntity>()
                        .lambda()
                        .eq(DbTableMetaEntity::getSourceKey, sourceKey)
                        .orderByAsc(DbTableMetaEntity::getTableName)
        );
        int rowIndex = 1;
        for (DbTableMetaEntity entity : entityList) {
            Row row = sheet.createRow(rowIndex++);
            writeRow(row, entity.getTableName(), entity.getTableComment(), entity.getTableType(),
                    entity.getLayerType(), entity.getRowCount(), entity.getColumnCount(), entity.getPartitionKey(),
                    entity.getFreshnessSeconds(), entity.getStatus(), entity.getEnabled(),
                    formatDateTime(entity.getLastScanAt()), formatDateTime(entity.getLastSyncAt()), entity.getRemark());
        }
        applyColumnLayout(sheet, sheetConfig);
        return sheet;
    }

    private Sheet writeFieldSheet(XSSFWorkbook workbook, String sourceKey, WorkbookTemplateContext templateContext) {
        DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = templateContext.getRequiredSheetConfig("field");
        Sheet sheet = workbook.createSheet(sheetConfig.getName());
        createHeader(workbook, sheet, sheetConfig);

        List<DbTableFieldMetaEntity> entityList = fieldMetaMapper.selectList(
                new QueryWrapper<DbTableFieldMetaEntity>()
                        .lambda()
                        .eq(DbTableFieldMetaEntity::getSourceKey, sourceKey)
                        .orderByAsc(DbTableFieldMetaEntity::getTableName, DbTableFieldMetaEntity::getOrdinalPosition)
        );
        int rowIndex = 1;
        for (DbTableFieldMetaEntity entity : entityList) {
            Row row = sheet.createRow(rowIndex++);
            writeRow(row, entity.getTableName(), entity.getColumnName(), entity.getColumnComment(),
                    entity.getDataType(), entity.getColumnLength(), entity.getColumnPrecision(), entity.getColumnScale(),
                    entity.getNullable(), entity.getPrimaryKey(), entity.getPartitionKey(), entity.getDefaultValue(),
                    entity.getOrdinalPosition(), entity.getFieldRole(), entity.getEnabled(), entity.getRemark());
        }
        applyColumnLayout(sheet, sheetConfig);
        return sheet;
    }

    private Sheet writeIndexSheet(XSSFWorkbook workbook, String sourceKey, WorkbookTemplateContext templateContext) {
        DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = templateContext.getRequiredSheetConfig("index");
        Sheet sheet = workbook.createSheet(sheetConfig.getName());
        createHeader(workbook, sheet, sheetConfig);

        List<DbTableIndexMetaEntity> entityList = indexMetaMapper.selectList(
                new QueryWrapper<DbTableIndexMetaEntity>()
                        .lambda()
                        .eq(DbTableIndexMetaEntity::getSourceKey, sourceKey)
                        .orderByAsc(DbTableIndexMetaEntity::getTableName, DbTableIndexMetaEntity::getIndexName, DbTableIndexMetaEntity::getColumnOrder)
        );
        int rowIndex = 1;
        for (DbTableIndexMetaEntity entity : entityList) {
            Row row = sheet.createRow(rowIndex++);
            writeRow(row, entity.getTableName(), entity.getIndexName(), entity.getIndexType(),
                    entity.getUniqueFlag(), entity.getPrimaryFlag(), entity.getColumnName(), entity.getColumnOrder(),
                    entity.getEnabled(), entity.getRemark());
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
        Map<String, Integer> columnIndexMap = buildColumnIndexMap(sheetConfig);
        DataFormatter formatter = new DataFormatter();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (isBlankRow(row)) {
                continue;
            }
            DbTableMetaExcelRow excelRow = new DbTableMetaExcelRow();
            excelRow.setSourceKey(resolveSourceKey(sourceKey));
            excelRow.setTableName(readRequiredString(row, requiredColumnIndex(columnIndexMap, "tableName"), formatter, sheetConfig.getName(), i, "tableName"));
            excelRow.setTableComment(readString(row, requiredColumnIndex(columnIndexMap, "tableComment"), formatter));
            excelRow.setTableType(readString(row, requiredColumnIndex(columnIndexMap, "tableType"), formatter));
            excelRow.setLayerType(readString(row, requiredColumnIndex(columnIndexMap, "layerType"), formatter));
            excelRow.setRowCount(readLong(row, requiredColumnIndex(columnIndexMap, "rowCount"), formatter));
            excelRow.setColumnCount(readInteger(row, requiredColumnIndex(columnIndexMap, "columnCount"), formatter));
            excelRow.setPartitionKey(readString(row, requiredColumnIndex(columnIndexMap, "partitionKey"), formatter));
            excelRow.setFreshnessSeconds(readInteger(row, requiredColumnIndex(columnIndexMap, "freshnessSeconds"), formatter));
            excelRow.setStatus(readString(row, requiredColumnIndex(columnIndexMap, "status"), formatter));
            excelRow.setEnabled(readBoolean(row, requiredColumnIndex(columnIndexMap, "enabled"), formatter));
            excelRow.setLastScanAt(readString(row, requiredColumnIndex(columnIndexMap, "lastScanAt"), formatter));
            excelRow.setLastSyncAt(readString(row, requiredColumnIndex(columnIndexMap, "lastSyncAt"), formatter));
            excelRow.setRemark(readString(row, requiredColumnIndex(columnIndexMap, "remark"), formatter));
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
        Map<String, Integer> columnIndexMap = buildColumnIndexMap(sheetConfig);
        DataFormatter formatter = new DataFormatter();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (isBlankRow(row)) {
                continue;
            }
            DbTableFieldMetaExcelRow excelRow = new DbTableFieldMetaExcelRow();
            excelRow.setSourceKey(resolveSourceKey(sourceKey));
            excelRow.setTableName(readRequiredString(row, requiredColumnIndex(columnIndexMap, "tableName"), formatter, sheetConfig.getName(), i, "tableName"));
            excelRow.setColumnName(readRequiredString(row, requiredColumnIndex(columnIndexMap, "columnName"), formatter, sheetConfig.getName(), i, "columnName"));
            excelRow.setColumnComment(readString(row, requiredColumnIndex(columnIndexMap, "columnComment"), formatter));
            excelRow.setDataType(readString(row, requiredColumnIndex(columnIndexMap, "dataType"), formatter));
            excelRow.setColumnLength(readInteger(row, requiredColumnIndex(columnIndexMap, "columnLength"), formatter));
            excelRow.setColumnPrecision(readInteger(row, requiredColumnIndex(columnIndexMap, "columnPrecision"), formatter));
            excelRow.setColumnScale(readInteger(row, requiredColumnIndex(columnIndexMap, "columnScale"), formatter));
            excelRow.setNullable(readBoolean(row, requiredColumnIndex(columnIndexMap, "nullable"), formatter));
            excelRow.setPrimaryKey(readBoolean(row, requiredColumnIndex(columnIndexMap, "primaryKey"), formatter));
            excelRow.setPartitionKey(readBoolean(row, requiredColumnIndex(columnIndexMap, "partitionKey"), formatter));
            excelRow.setDefaultValue(readString(row, requiredColumnIndex(columnIndexMap, "defaultValue"), formatter));
            excelRow.setOrdinalPosition(readInteger(row, requiredColumnIndex(columnIndexMap, "ordinalPosition"), formatter));
            excelRow.setFieldRole(readString(row, requiredColumnIndex(columnIndexMap, "fieldRole"), formatter));
            excelRow.setEnabled(readBoolean(row, requiredColumnIndex(columnIndexMap, "enabled"), formatter));
            excelRow.setRemark(readString(row, requiredColumnIndex(columnIndexMap, "remark"), formatter));
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
        Map<String, Integer> columnIndexMap = buildColumnIndexMap(sheetConfig);
        DataFormatter formatter = new DataFormatter();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (isBlankRow(row)) {
                continue;
            }
            DbTableIndexMetaExcelRow excelRow = new DbTableIndexMetaExcelRow();
            excelRow.setSourceKey(resolveSourceKey(sourceKey));
            excelRow.setTableName(readRequiredString(row, requiredColumnIndex(columnIndexMap, "tableName"), formatter, sheetConfig.getName(), i, "tableName"));
            excelRow.setIndexName(readRequiredString(row, requiredColumnIndex(columnIndexMap, "indexName"), formatter, sheetConfig.getName(), i, "indexName"));
            excelRow.setIndexType(readString(row, requiredColumnIndex(columnIndexMap, "indexType"), formatter));
            excelRow.setUniqueFlag(readBoolean(row, requiredColumnIndex(columnIndexMap, "uniqueFlag"), formatter));
            excelRow.setPrimaryFlag(readBoolean(row, requiredColumnIndex(columnIndexMap, "primaryFlag"), formatter));
            excelRow.setColumnName(readRequiredString(row, requiredColumnIndex(columnIndexMap, "columnName"), formatter, sheetConfig.getName(), i, "columnName"));
            excelRow.setColumnOrder(readInteger(row, requiredColumnIndex(columnIndexMap, "columnOrder"), formatter));
            excelRow.setEnabled(readBoolean(row, requiredColumnIndex(columnIndexMap, "enabled"), formatter));
            excelRow.setRemark(readString(row, requiredColumnIndex(columnIndexMap, "remark"), formatter));
            rowList.add(excelRow);
        }
        return rowList;
    }

    private DbTableMetaEntity findExistingTable(String sourceKey, String tableName) {
        return tableMetaMapper.selectOne(
                new QueryWrapper<DbTableMetaEntity>()
                        .lambda()
                        .eq(DbTableMetaEntity::getSourceKey, sourceKey)
                        .eq(DbTableMetaEntity::getTableName, tableName)
        );
    }

    private DbTableFieldMetaEntity findExistingField(String sourceKey, String tableName, String columnName) {
        return fieldMetaMapper.selectOne(
                new QueryWrapper<DbTableFieldMetaEntity>()
                        .lambda()
                        .eq(DbTableFieldMetaEntity::getSourceKey, sourceKey)
                        .eq(DbTableFieldMetaEntity::getTableName, tableName)
                        .eq(DbTableFieldMetaEntity::getColumnName, columnName)
        );
    }

    private DbTableIndexMetaEntity findExistingIndex(String sourceKey, String tableName, String indexName, String columnName) {
        return indexMetaMapper.selectOne(
                new QueryWrapper<DbTableIndexMetaEntity>()
                        .lambda()
                        .eq(DbTableIndexMetaEntity::getSourceKey, sourceKey)
                        .eq(DbTableIndexMetaEntity::getTableName, tableName)
                        .eq(DbTableIndexMetaEntity::getIndexName, indexName)
                        .eq(DbTableIndexMetaEntity::getColumnName, columnName)
        );
    }

    private DbTableMetaEntity toTableEntity(DbTableMetaExcelRow row, DbTableMetaEntity existing) {
        DbTableMetaEntity entity = existing == null ? new DbTableMetaEntity() : existing;
        entity.setSourceKey(row.getSourceKey());
        entity.setTableName(row.getTableName());
        entity.setTableComment(row.getTableComment());
        entity.setTableType(row.getTableType());
        entity.setLayerType(row.getLayerType());
        entity.setRowCount(row.getRowCount());
        entity.setColumnCount(row.getColumnCount());
        entity.setPartitionKey(row.getPartitionKey());
        entity.setFreshnessSeconds(row.getFreshnessSeconds());
        entity.setStatus(row.getStatus());
        entity.setEnabled(defaultBoolean(row.getEnabled()));
        entity.setLastScanAt(parseDateTime(row.getLastScanAt()));
        entity.setLastSyncAt(parseDateTime(row.getLastSyncAt()));
        entity.setRemark(row.getRemark());
        return entity;
    }

    private DbTableFieldMetaEntity toFieldEntity(DbTableFieldMetaExcelRow row, DbTableFieldMetaEntity existing) {
        DbTableFieldMetaEntity entity = existing == null ? new DbTableFieldMetaEntity() : existing;
        entity.setSourceKey(row.getSourceKey());
        entity.setTableName(row.getTableName());
        entity.setColumnName(row.getColumnName());
        entity.setColumnComment(row.getColumnComment());
        entity.setDataType(row.getDataType());
        entity.setColumnLength(row.getColumnLength());
        entity.setColumnPrecision(row.getColumnPrecision());
        entity.setColumnScale(row.getColumnScale());
        entity.setNullable(defaultBoolean(row.getNullable()));
        entity.setPrimaryKey(defaultBoolean(row.getPrimaryKey()));
        entity.setPartitionKey(defaultBoolean(row.getPartitionKey()));
        entity.setDefaultValue(row.getDefaultValue());
        entity.setOrdinalPosition(row.getOrdinalPosition());
        entity.setFieldRole(row.getFieldRole());
        entity.setEnabled(defaultBoolean(row.getEnabled()));
        entity.setRemark(row.getRemark());
        return entity;
    }

    private DbTableIndexMetaEntity toIndexEntity(DbTableIndexMetaExcelRow row, DbTableIndexMetaEntity existing) {
        DbTableIndexMetaEntity entity = existing == null ? new DbTableIndexMetaEntity() : existing;
        entity.setSourceKey(row.getSourceKey());
        entity.setTableName(row.getTableName());
        entity.setIndexName(row.getIndexName());
        entity.setIndexType(row.getIndexType());
        entity.setUniqueFlag(defaultBoolean(row.getUniqueFlag()));
        entity.setPrimaryFlag(defaultBoolean(row.getPrimaryFlag()));
        entity.setColumnName(row.getColumnName());
        entity.setColumnOrder(row.getColumnOrder());
        entity.setEnabled(defaultBoolean(row.getEnabled()));
        entity.setRemark(row.getRemark());
        return entity;
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
        CellStyle headerStyle = workbook.createCellStyle();
        for (int i = 0; i < sheetConfig.getColumns().size(); i++) {
            DbMetaWorkbookTemplateConfig.ColumnConfig columnConfig = sheetConfig.getColumns().get(i);
            Cell cell = row.createCell(i);
            cell.setCellValue(columnConfig.getLabel());
            cell.setCellStyle(headerStyle);
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

    private String readRequiredString(Row row, int index, DataFormatter formatter, String sheetName, int rowIndex, String fieldName) {
        String value = readString(row, index, formatter);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("sheet[" + sheetName + "] 第 " + (rowIndex + 1) + " 行缺少必填字段 " + fieldName);
        }
        return value;
    }

    private String readString(Row row, int index, DataFormatter formatter) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Integer readInteger(Row row, int index, DataFormatter formatter) {
        String value = readString(row, index, formatter);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private Long readLong(Row row, int index, DataFormatter formatter) {
        String value = readString(row, index, formatter);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Long.parseLong(value);
    }

    private Boolean readBoolean(Row row, int index, DataFormatter formatter) {
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
            WorkbookTemplateContext templateContext
    ) {
        Map<String, Sheet> sheetByKey = Map.of(
                "table", tableSheet,
                "field", fieldSheet,
                "index", indexSheet
        );
        Sheet optionSheet = workbook.createSheet(OPTION_SHEET_NAME);
        workbook.setSheetHidden(workbook.getSheetIndex(optionSheet), true);

        int optionColumnIndex = 0;
        for (DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig : templateContext.getTemplateConfig().getSheets()) {
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
                DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = templateContext.getRequiredSheetConfig(namedRangeConfig.getSheetKey());
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
    }
}
