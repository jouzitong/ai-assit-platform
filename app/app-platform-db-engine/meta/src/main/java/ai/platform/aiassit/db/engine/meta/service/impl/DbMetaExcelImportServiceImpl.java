package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.entity.excel.DbMetaWorkbookTemplateConfig;
import ai.platform.aiassit.db.engine.meta.entity.importer.DbMetaImportData;
import ai.platform.aiassit.db.engine.meta.service.importer.DbMetaImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DbMetaExcelImportServiceImpl implements DbMetaImportService {

    private static final String TEMPLATE_CONFIG_CLASSPATH = "db-meta-workbook-template.json";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String templateConfigLocation;

    public DbMetaExcelImportServiceImpl(
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            @Value("${aiassit.db.meta.workbook.template-config-location:}") String templateConfigLocation
    ) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.templateConfigLocation = templateConfigLocation;
    }

    @Override
    public boolean supports(MultipartFile file) {
        String filename = file == null ? null : file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(filename);
        return "xlsx".equalsIgnoreCase(extension);
    }

    @Override
    public String getFormat() {
        return "excel";
    }

    @Override
    public DbMetaImportData parse(String sourceKey, MultipartFile file) throws IOException {
        WorkbookTemplateContext templateContext = loadTemplateContext();
        try (InputStream inputStream = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            DbMetaImportData importData = new DbMetaImportData();
            importData.setTables(readTableRows(sourceKey, workbook.getSheet(templateContext.resolveSheetName("table")), templateContext));
            importData.setFields(readFieldRows(sourceKey, workbook.getSheet(templateContext.resolveSheetName("field")), templateContext));
            importData.setIndexes(readIndexRows(sourceKey, workbook.getSheet(templateContext.resolveSheetName("index")), templateContext));
            return importData;
        }
    }

    private List<DbMetaImportData.TableRow> readTableRows(String sourceKey, Sheet sheet, WorkbookTemplateContext templateContext) {
        List<DbMetaImportData.TableRow> rowList = new java.util.ArrayList<>();
        if (sheet == null) {
            return rowList;
        }
        DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = templateContext.getRequiredSheetConfig("table");
        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> columnIndexMap = buildSheetColumnIndexMap(sheet, sheetConfig, formatter);
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (isBlankRow(row, formatter)) {
                continue;
            }
            DbMetaImportData.TableRow importRow = new DbMetaImportData.TableRow();
            importRow.setTableName(readRequiredString(row, columnIndexMap.get("tableName"), formatter, sheetConfig.getName(), i, "tableName"));
            importRow.setTableComment(readString(row, columnIndexMap.get("tableComment"), formatter));
            importRow.setTableType(readString(row, columnIndexMap.get("tableType"), formatter));
            importRow.setLayerType(readString(row, columnIndexMap.get("layerType"), formatter));
            importRow.setRowCount(readLong(row, columnIndexMap.get("rowCount"), formatter));
            importRow.setColumnCount(readInteger(row, columnIndexMap.get("columnCount"), formatter));
            importRow.setPartitionKey(readString(row, columnIndexMap.get("partitionKey"), formatter));
            importRow.setFreshnessSeconds(readInteger(row, columnIndexMap.get("freshnessSeconds"), formatter));
            importRow.setStatus(readString(row, columnIndexMap.get("status"), formatter));
            importRow.setEnabled(readBoolean(row, columnIndexMap.get("enabled"), formatter));
            importRow.setLastScanAt(readString(row, columnIndexMap.get("lastScanAt"), formatter));
            importRow.setLastSyncAt(readString(row, columnIndexMap.get("lastSyncAt"), formatter));
            importRow.setRemark(readString(row, columnIndexMap.get("remark"), formatter));
            rowList.add(importRow);
        }
        return rowList;
    }

    private List<DbMetaImportData.FieldRow> readFieldRows(String sourceKey, Sheet sheet, WorkbookTemplateContext templateContext) {
        List<DbMetaImportData.FieldRow> rowList = new java.util.ArrayList<>();
        if (sheet == null) {
            return rowList;
        }
        DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = templateContext.getRequiredSheetConfig("field");
        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> columnIndexMap = buildSheetColumnIndexMap(sheet, sheetConfig, formatter);
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (isBlankRow(row, formatter)) {
                continue;
            }
            DbMetaImportData.FieldRow importRow = new DbMetaImportData.FieldRow();
            importRow.setTableName(readRequiredString(row, columnIndexMap.get("tableName"), formatter, sheetConfig.getName(), i, "tableName"));
            importRow.setColumnName(readRequiredString(row, columnIndexMap.get("columnName"), formatter, sheetConfig.getName(), i, "columnName"));
            importRow.setColumnComment(readString(row, columnIndexMap.get("columnComment"), formatter));
            importRow.setDataType(readString(row, columnIndexMap.get("dataType"), formatter));
            importRow.setColumnLength(readInteger(row, columnIndexMap.get("columnLength"), formatter));
            importRow.setColumnPrecision(readInteger(row, columnIndexMap.get("columnPrecision"), formatter));
            importRow.setColumnScale(readInteger(row, columnIndexMap.get("columnScale"), formatter));
            importRow.setNullable(readBoolean(row, columnIndexMap.get("nullable"), formatter));
            importRow.setPrimaryKey(readBoolean(row, columnIndexMap.get("primaryKey"), formatter));
            importRow.setPartitionKey(readBoolean(row, columnIndexMap.get("partitionKey"), formatter));
            importRow.setDefaultValue(readString(row, columnIndexMap.get("defaultValue"), formatter));
            importRow.setOrdinalPosition(readInteger(row, columnIndexMap.get("ordinalPosition"), formatter));
            importRow.setFieldRole(readString(row, columnIndexMap.get("fieldRole"), formatter));
            importRow.setEnabled(readBoolean(row, columnIndexMap.get("enabled"), formatter));
            importRow.setRemark(readString(row, columnIndexMap.get("remark"), formatter));
            rowList.add(importRow);
        }
        return rowList;
    }

    private List<DbMetaImportData.IndexRow> readIndexRows(String sourceKey, Sheet sheet, WorkbookTemplateContext templateContext) {
        List<DbMetaImportData.IndexRow> rowList = new java.util.ArrayList<>();
        if (sheet == null) {
            return rowList;
        }
        DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = templateContext.getRequiredSheetConfig("index");
        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> columnIndexMap = buildSheetColumnIndexMap(sheet, sheetConfig, formatter);
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (isBlankRow(row, formatter)) {
                continue;
            }
            DbMetaImportData.IndexRow importRow = new DbMetaImportData.IndexRow();
            importRow.setTableName(readRequiredString(row, columnIndexMap.get("tableName"), formatter, sheetConfig.getName(), i, "tableName"));
            importRow.setIndexName(readRequiredString(row, columnIndexMap.get("indexName"), formatter, sheetConfig.getName(), i, "indexName"));
            importRow.setIndexType(readString(row, columnIndexMap.get("indexType"), formatter));
            importRow.setUniqueFlag(readBoolean(row, columnIndexMap.get("uniqueFlag"), formatter));
            importRow.setPrimaryFlag(readBoolean(row, columnIndexMap.get("primaryFlag"), formatter));
            importRow.setColumnName(readRequiredString(row, columnIndexMap.get("columnName"), formatter, sheetConfig.getName(), i, "columnName"));
            importRow.setColumnOrder(readInteger(row, columnIndexMap.get("columnOrder"), formatter));
            importRow.setEnabled(readBoolean(row, columnIndexMap.get("enabled"), formatter));
            importRow.setRemark(readString(row, columnIndexMap.get("remark"), formatter));
            rowList.add(importRow);
        }
        return rowList;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && StringUtils.hasText(formatter.formatCellValue(cell))) {
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
                        sheetConfig -> filterSheetConfig(sheetConfig),
                        (left, right) -> left,
                        HashMap::new
                ));
    }

    private DbMetaWorkbookTemplateConfig.SheetConfig filterSheetConfig(DbMetaWorkbookTemplateConfig.SheetConfig source) {
        DbMetaWorkbookTemplateConfig.SheetConfig sheetConfig = new DbMetaWorkbookTemplateConfig.SheetConfig();
        sheetConfig.setKey(source.getKey());
        sheetConfig.setName(source.getName());
        sheetConfig.setColumns(source.getColumns().stream()
                .filter(columnConfig -> !Boolean.FALSE.equals(columnConfig.getImportable()))
                .collect(Collectors.toList()));
        return sheetConfig;
    }

    private static final class WorkbookTemplateContext {

        private final Map<String, DbMetaWorkbookTemplateConfig.SheetConfig> sheetConfigByKey;

        private WorkbookTemplateContext(
                DbMetaWorkbookTemplateConfig templateConfig,
                Map<String, DbMetaWorkbookTemplateConfig.SheetConfig> sheetConfigByKey
        ) {
            this.sheetConfigByKey = sheetConfigByKey;
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
