package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.entity.DbTableFieldMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.DbTableIndexMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.DbTableMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportResultDTO;
import ai.platform.aiassit.db.engine.meta.entity.excel.DbTableFieldMetaExcelRow;
import ai.platform.aiassit.db.engine.meta.entity.excel.DbTableIndexMetaExcelRow;
import ai.platform.aiassit.db.engine.meta.entity.excel.DbTableMetaExcelRow;
import ai.platform.aiassit.db.engine.meta.mapper.DbTableFieldMetaMapper;
import ai.platform.aiassit.db.engine.meta.mapper.DbTableIndexMetaMapper;
import ai.platform.aiassit.db.engine.meta.mapper.DbTableMetaMapper;
import ai.platform.aiassit.db.engine.meta.service.DbMetaWorkbookService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DbMetaWorkbookServiceImpl implements DbMetaWorkbookService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String TABLE_SHEET_NAME = "表说明";
    private static final String FIELD_SHEET_NAME = "字段说明";
    private static final String INDEX_SHEET_NAME = "索引说明";
    private static final String OPTION_SHEET_NAME = "_options";

    private static final int DATA_VALIDATION_FIRST_ROW = 1;
    private static final int DATA_VALIDATION_LAST_ROW = 2000;

    private static final String TABLE_NAME_RANGE = "table_name_options";
    private static final String FIELD_NAME_RANGE = "field_name_options";

    private static final String[] TABLE_TYPE_OPTIONS = {"TABLE", "VIEW", "API_OBJECT"};
    private static final String[] LAYER_TYPE_OPTIONS = {"ODS", "DWD", "DWS", "ADS"};
    private static final String[] STATUS_OPTIONS = {"ACTIVE", "PENDING", "INACTIVE"};
    private static final String[] BOOLEAN_OPTIONS = {"true", "false"};
    private static final String[] FIELD_ROLE_OPTIONS = {"DIMENSION", "METRIC", "TIME", "ATTRIBUTE"};
    private static final String[] INDEX_TYPE_OPTIONS = {"PRIMARY", "UNIQUE", "NORMAL"};
    private static final String[] DATA_TYPE_OPTIONS = {
            "varchar", "char", "text", "bigint", "int", "integer", "decimal", "double", "float",
            "boolean", "datetime", "timestamp", "date", "json"
    };

    private final DbTableMetaMapper tableMetaMapper;
    private final DbTableFieldMetaMapper fieldMetaMapper;
    private final DbTableIndexMetaMapper indexMetaMapper;

    public DbMetaWorkbookServiceImpl(
            DbTableMetaMapper tableMetaMapper,
            DbTableFieldMetaMapper fieldMetaMapper,
            DbTableIndexMetaMapper indexMetaMapper
    ) {
        this.tableMetaMapper = tableMetaMapper;
        this.fieldMetaMapper = fieldMetaMapper;
        this.indexMetaMapper = indexMetaMapper;
    }

    @Override
    public byte[] exportWorkbook(String sourceKey) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet tableSheet = writeTableSheet(workbook, sourceKey);
            Sheet fieldSheet = writeFieldSheet(workbook, sourceKey);
            Sheet indexSheet = writeIndexSheet(workbook, sourceKey);
            configureWorkbookValidations(workbook, tableSheet, fieldSheet, indexSheet);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    @Override
    public byte[] exportTemplateWorkbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet tableSheet = createTableSheetTemplate(workbook);
            Sheet fieldSheet = createFieldSheetTemplate(workbook);
            Sheet indexSheet = createIndexSheetTemplate(workbook);
            configureWorkbookValidations(workbook, tableSheet, fieldSheet, indexSheet);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    @Override
    public DbMetaImportResultDTO importWorkbook(String sourceKey, MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            List<DbTableMetaExcelRow> tableRows = readTableRows(sourceKey, workbook.getSheet(TABLE_SHEET_NAME));
            List<DbTableFieldMetaExcelRow> fieldRows = readFieldRows(sourceKey, workbook.getSheet(FIELD_SHEET_NAME));
            List<DbTableIndexMetaExcelRow> indexRows = readIndexRows(sourceKey, workbook.getSheet(INDEX_SHEET_NAME));

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

    private Sheet writeTableSheet(XSSFWorkbook workbook, String sourceKey) {
        Sheet sheet = workbook.createSheet(TABLE_SHEET_NAME);
        createHeader(sheet, "表名", "表中文说明", "表类型", "分层类型", "数据量", "字段数",
                "分区键", "新鲜度(秒)", "状态", "是否启用", "最近扫描时间", "最近同步时间", "备注");

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
        autoSize(sheet, 13);
        return sheet;
    }

    private Sheet createTableSheetTemplate(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet(TABLE_SHEET_NAME);
        createHeader(sheet, "表名", "表中文说明", "表类型", "分层类型", "数据量", "字段数",
                "分区键", "新鲜度(秒)", "状态", "是否启用", "最近扫描时间", "最近同步时间", "备注");
        autoSize(sheet, 13);
        return sheet;
    }

    private Sheet writeFieldSheet(XSSFWorkbook workbook, String sourceKey) {
        Sheet sheet = workbook.createSheet(FIELD_SHEET_NAME);
        createHeader(sheet, "表名", "字段名", "字段中文说明", "字段类型", "字段长度",
                "数值精度", "数值小数位", "是否可空", "是否主键", "是否分区键", "默认值",
                "字段顺序", "字段角色", "是否启用", "备注");

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
        autoSize(sheet, 15);
        return sheet;
    }

    private Sheet createFieldSheetTemplate(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet(FIELD_SHEET_NAME);
        createHeader(sheet, "表名", "字段名", "字段中文说明", "字段类型", "字段长度",
                "数值精度", "数值小数位", "是否可空", "是否主键", "是否分区键", "默认值",
                "字段顺序", "字段角色", "是否启用", "备注");
        autoSize(sheet, 15);
        return sheet;
    }

    private Sheet writeIndexSheet(XSSFWorkbook workbook, String sourceKey) {
        Sheet sheet = workbook.createSheet(INDEX_SHEET_NAME);
        createHeader(sheet, "表名", "索引名称", "索引类型", "是否唯一索引", "是否主键索引",
                "索引字段名", "字段顺序", "是否启用", "备注");

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
        autoSize(sheet, 9);
        return sheet;
    }

    private Sheet createIndexSheetTemplate(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet(INDEX_SHEET_NAME);
        createHeader(sheet, "表名", "索引名称", "索引类型", "是否唯一索引", "是否主键索引",
                "索引字段名", "字段顺序", "是否启用", "备注");
        autoSize(sheet, 9);
        return sheet;
    }

    private List<DbTableMetaExcelRow> readTableRows(String sourceKey, Sheet sheet) {
        List<DbTableMetaExcelRow> rowList = new ArrayList<>();
        if (sheet == null) {
            return rowList;
        }
        DataFormatter formatter = new DataFormatter();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (isBlankRow(row)) {
                continue;
            }
            DbTableMetaExcelRow excelRow = new DbTableMetaExcelRow();
            excelRow.setSourceKey(resolveSourceKey(sourceKey));
            excelRow.setTableName(readRequiredString(row, 0, formatter, TABLE_SHEET_NAME, i, "tableName"));
            excelRow.setTableComment(readString(row, 1, formatter));
            excelRow.setTableType(readString(row, 2, formatter));
            excelRow.setLayerType(readString(row, 3, formatter));
            excelRow.setRowCount(readLong(row, 4, formatter));
            excelRow.setColumnCount(readInteger(row, 5, formatter));
            excelRow.setPartitionKey(readString(row, 6, formatter));
            excelRow.setFreshnessSeconds(readInteger(row, 7, formatter));
            excelRow.setStatus(readString(row, 8, formatter));
            excelRow.setEnabled(readBoolean(row, 9, formatter));
            excelRow.setLastScanAt(readString(row, 10, formatter));
            excelRow.setLastSyncAt(readString(row, 11, formatter));
            excelRow.setRemark(readString(row, 12, formatter));
            rowList.add(excelRow);
        }
        return rowList;
    }

    private List<DbTableFieldMetaExcelRow> readFieldRows(String sourceKey, Sheet sheet) {
        List<DbTableFieldMetaExcelRow> rowList = new ArrayList<>();
        if (sheet == null) {
            return rowList;
        }
        DataFormatter formatter = new DataFormatter();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (isBlankRow(row)) {
                continue;
            }
            DbTableFieldMetaExcelRow excelRow = new DbTableFieldMetaExcelRow();
            excelRow.setSourceKey(resolveSourceKey(sourceKey));
            excelRow.setTableName(readRequiredString(row, 0, formatter, FIELD_SHEET_NAME, i, "tableName"));
            excelRow.setColumnName(readRequiredString(row, 1, formatter, FIELD_SHEET_NAME, i, "columnName"));
            excelRow.setColumnComment(readString(row, 2, formatter));
            excelRow.setDataType(readString(row, 3, formatter));
            excelRow.setColumnLength(readInteger(row, 4, formatter));
            excelRow.setColumnPrecision(readInteger(row, 5, formatter));
            excelRow.setColumnScale(readInteger(row, 6, formatter));
            excelRow.setNullable(readBoolean(row, 7, formatter));
            excelRow.setPrimaryKey(readBoolean(row, 8, formatter));
            excelRow.setPartitionKey(readBoolean(row, 9, formatter));
            excelRow.setDefaultValue(readString(row, 10, formatter));
            excelRow.setOrdinalPosition(readInteger(row, 11, formatter));
            excelRow.setFieldRole(readString(row, 12, formatter));
            excelRow.setEnabled(readBoolean(row, 13, formatter));
            excelRow.setRemark(readString(row, 14, formatter));
            rowList.add(excelRow);
        }
        return rowList;
    }

    private List<DbTableIndexMetaExcelRow> readIndexRows(String sourceKey, Sheet sheet) {
        List<DbTableIndexMetaExcelRow> rowList = new ArrayList<>();
        if (sheet == null) {
            return rowList;
        }
        DataFormatter formatter = new DataFormatter();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (isBlankRow(row)) {
                continue;
            }
            DbTableIndexMetaExcelRow excelRow = new DbTableIndexMetaExcelRow();
            excelRow.setSourceKey(resolveSourceKey(sourceKey));
            excelRow.setTableName(readRequiredString(row, 0, formatter, INDEX_SHEET_NAME, i, "tableName"));
            excelRow.setIndexName(readRequiredString(row, 1, formatter, INDEX_SHEET_NAME, i, "indexName"));
            excelRow.setIndexType(readString(row, 2, formatter));
            excelRow.setUniqueFlag(readBoolean(row, 3, formatter));
            excelRow.setPrimaryFlag(readBoolean(row, 4, formatter));
            excelRow.setColumnName(readRequiredString(row, 5, formatter, INDEX_SHEET_NAME, i, "columnName"));
            excelRow.setColumnOrder(readInteger(row, 6, formatter));
            excelRow.setEnabled(readBoolean(row, 7, formatter));
            excelRow.setRemark(readString(row, 8, formatter));
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

    private void createHeader(Sheet sheet, String... headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            row.createCell(i).setCellValue(headers[i]);
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

    private void autoSize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            int width = Math.min(sheet.getColumnWidth(i) + 1024, 40 * 256);
            sheet.setColumnWidth(i, width);
        }
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
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
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

    private void configureWorkbookValidations(XSSFWorkbook workbook, Sheet tableSheet, Sheet fieldSheet, Sheet indexSheet) {
        Sheet optionSheet = workbook.createSheet(OPTION_SHEET_NAME);
        workbook.setSheetHidden(workbook.getSheetIndex(optionSheet), true);

        writeOptionColumn(optionSheet, 0, TABLE_TYPE_OPTIONS);
        writeOptionColumn(optionSheet, 1, LAYER_TYPE_OPTIONS);
        writeOptionColumn(optionSheet, 2, STATUS_OPTIONS);
        writeOptionColumn(optionSheet, 3, BOOLEAN_OPTIONS);
        writeOptionColumn(optionSheet, 4, FIELD_ROLE_OPTIONS);
        writeOptionColumn(optionSheet, 5, INDEX_TYPE_OPTIONS);
        writeOptionColumn(optionSheet, 6, DATA_TYPE_OPTIONS);

        createNamedRange(workbook, "table_type_options", OPTION_SHEET_NAME, 0, 1, TABLE_TYPE_OPTIONS.length);
        createNamedRange(workbook, "layer_type_options", OPTION_SHEET_NAME, 1, 1, LAYER_TYPE_OPTIONS.length);
        createNamedRange(workbook, "status_options", OPTION_SHEET_NAME, 2, 1, STATUS_OPTIONS.length);
        createNamedRange(workbook, "boolean_options", OPTION_SHEET_NAME, 3, 1, BOOLEAN_OPTIONS.length);
        createNamedRange(workbook, "field_role_options", OPTION_SHEET_NAME, 4, 1, FIELD_ROLE_OPTIONS.length);
        createNamedRange(workbook, "index_type_options", OPTION_SHEET_NAME, 5, 1, INDEX_TYPE_OPTIONS.length);
        createNamedRange(workbook, "data_type_options", OPTION_SHEET_NAME, 6, 1, DATA_TYPE_OPTIONS.length);
        createNamedRange(workbook, TABLE_NAME_RANGE, TABLE_SHEET_NAME, 0, 2, DATA_VALIDATION_LAST_ROW + 1);
        createNamedRange(workbook, FIELD_NAME_RANGE, FIELD_SHEET_NAME, 1, 2, DATA_VALIDATION_LAST_ROW + 1);

        addNamedRangeValidation(tableSheet, "table_type_options", 2);
        addNamedRangeValidation(tableSheet, "layer_type_options", 3);
        addNamedRangeValidation(tableSheet, "status_options", 8);
        addNamedRangeValidation(tableSheet, "boolean_options", 9);

        addNamedRangeValidation(fieldSheet, TABLE_NAME_RANGE, 0);
        addNamedRangeValidation(fieldSheet, "data_type_options", 3);
        addNamedRangeValidation(fieldSheet, "boolean_options", 7);
        addNamedRangeValidation(fieldSheet, "boolean_options", 8);
        addNamedRangeValidation(fieldSheet, "boolean_options", 9);
        addNamedRangeValidation(fieldSheet, "field_role_options", 12);
        addNamedRangeValidation(fieldSheet, "boolean_options", 13);

        addNamedRangeValidation(indexSheet, TABLE_NAME_RANGE, 0);
        addNamedRangeValidation(indexSheet, "index_type_options", 2);
        addNamedRangeValidation(indexSheet, "boolean_options", 3);
        addNamedRangeValidation(indexSheet, "boolean_options", 4);
        addNamedRangeValidation(indexSheet, FIELD_NAME_RANGE, 5);
        addNamedRangeValidation(indexSheet, "boolean_options", 7);
    }

    private void writeOptionColumn(Sheet optionSheet, int columnIndex, String[] values) {
        for (int i = 0; i < values.length; i++) {
            Row row = optionSheet.getRow(i);
            if (row == null) {
                row = optionSheet.createRow(i);
            }
            row.createCell(columnIndex).setCellValue(values[i]);
        }
    }

    private void createNamedRange(
            XSSFWorkbook workbook,
            String rangeName,
            String sheetName,
            int columnIndex,
            int startRow,
            int endRow
    ) {
        Name namedRange = workbook.createName();
        namedRange.setNameName(rangeName);
        String columnLetter = toColumnLetter(columnIndex);
        namedRange.setRefersToFormula("'" + sheetName + "'!$" + columnLetter + "$" + startRow + ":$" + columnLetter + "$" + endRow);
    }

    private void addNamedRangeValidation(Sheet sheet, String rangeName, int columnIndex) {
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
}
