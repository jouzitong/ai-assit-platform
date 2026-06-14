package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableIndexMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import ai.platform.aiassit.db.engine.meta.entity.excel.DbMetaWorkbookTemplateConfig;
import ai.platform.aiassit.db.engine.meta.entity.importer.DbMetaImportData;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableFieldMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableIndexMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.entity.req.DbTableMetaQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbMetaWorkbookService;
import ai.platform.aiassit.db.engine.meta.service.importer.DbMetaImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final List<DbMetaImportService> importServices;
    private final DbMetaImportExecutor importExecutor;

    public DbMetaWorkbookServiceImpl(
            DbTableMetaServiceImpl tableMetaService,
            DbTableFieldMetaServiceImpl fieldMetaService,
            DbTableIndexMetaServiceImpl indexMetaService,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            List<DbMetaImportService> importServices,
            DbMetaImportExecutor importExecutor,
            @Value("${aiassit.db.meta.workbook.template-config-location:}") String templateConfigLocation
    ) {
        this.tableMetaService = tableMetaService;
        this.fieldMetaService = fieldMetaService;
        this.indexMetaService = indexMetaService;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.importServices = importServices;
        this.importExecutor = importExecutor;
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
    public ai.platform.aiassit.db.engine.meta.entity.dto.DbMetaImportResultDTO importWorkbook(String sourceKey, MultipartFile file) throws IOException {
        DbMetaImportService importService = importServices.stream()
                .filter(service -> service.supports(file))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("暂不支持的导入文件格式: " + resolveFilename(file)));
        DbMetaImportData importData = importService.parse(sourceKey, file);
        return importExecutor.importData(sourceKey, file, importService.getFormat(), importData);
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

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
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

    private String resolveFilename(MultipartFile file) {
        return file == null ? "" : StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "";
    }

    private enum SheetMode {
        IMPORT,
        EXPORT
    }

    private final class WorkbookTemplateContext {

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

        private DbMetaWorkbookTemplateConfig.SheetConfig getRequiredSheetConfig(String sheetKey, SheetMode sheetMode) {
            DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = getOptionalSheetConfig(sheetKey, sheetMode);
            if (sheetConfig == null) {
                throw new IllegalStateException("工作簿模板缺少 sheet: " + sheetKey);
            }
            return sheetConfig;
        }

        private DbMetaWorkbookTemplateConfig.SheetConfig getOptionalSheetConfig(String sheetKey, SheetMode sheetMode) {
            DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = sheetConfigByKey.get(sheetKey);
            if (sheetConfig == null) {
                return null;
            }
            return filterSheetConfig(sheetConfig, sheetMode);
        }

        private List<DbMetaWorkbookTemplateConfig.SheetConfig> getSheetConfigs(SheetMode sheetMode) {
            return sheetConfigByKey.values().stream()
                    .map(sheetConfig -> filterSheetConfig(sheetConfig, sheetMode))
                    .toList();
        }
    }
}
