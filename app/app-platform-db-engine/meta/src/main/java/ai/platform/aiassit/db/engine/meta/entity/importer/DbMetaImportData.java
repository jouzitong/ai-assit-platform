package ai.platform.aiassit.db.engine.meta.entity.importer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DbMetaImportData {

    private List<TableRow> tables = new ArrayList<>();
    private List<FieldRow> fields = new ArrayList<>();
    private List<IndexRow> indexes = new ArrayList<>();

    public static DbMetaImportData createTemplateSample() {
        DbMetaImportData sample = new DbMetaImportData();

        TableRow tableRow = new TableRow();
        tableRow.setTableName("employee_profile");
        tableRow.setTableComment("员工档案表");
        tableRow.setTableType("BASE TABLE");
        tableRow.setLayerType("ODS");
        tableRow.setRowCount(12000L);
        tableRow.setColumnCount(3);
        tableRow.setPartitionKey("dt");
        tableRow.setFreshnessSeconds(300);
        tableRow.setStatus("ACTIVE");
        tableRow.setEnabled(Boolean.TRUE);
        tableRow.setLastScanAt("2026-06-14 10:00:00");
        tableRow.setLastSyncAt("2026-06-14 10:05:00");
        tableRow.setRemark("模板示例：请按真实业务替换表名和字段定义");
        sample.getTables().add(tableRow);

        FieldRow idField = new FieldRow();
        idField.setTableName("employee_profile");
        idField.setColumnName("employee_id");
        idField.setColumnComment("员工 ID");
        idField.setDataType("bigint");
        idField.setNullable(Boolean.FALSE);
        idField.setPrimaryKey(Boolean.TRUE);
        idField.setPartitionKey(Boolean.FALSE);
        idField.setOrdinalPosition(1);
        idField.setFieldRole("PRIMARY_KEY");
        idField.setEnabled(Boolean.TRUE);
        idField.setRemark("模板示例：主键字段");
        sample.getFields().add(idField);

        FieldRow nameField = new FieldRow();
        nameField.setTableName("employee_profile");
        nameField.setColumnName("employee_name");
        nameField.setColumnComment("员工姓名");
        nameField.setDataType("varchar");
        nameField.setColumnLength(64);
        nameField.setNullable(Boolean.FALSE);
        nameField.setPrimaryKey(Boolean.FALSE);
        nameField.setPartitionKey(Boolean.FALSE);
        nameField.setOrdinalPosition(2);
        nameField.setFieldRole("DIMENSION");
        nameField.setEnabled(Boolean.TRUE);
        nameField.setRemark("模板示例：普通维度字段");
        sample.getFields().add(nameField);

        FieldRow dtField = new FieldRow();
        dtField.setTableName("employee_profile");
        dtField.setColumnName("dt");
        dtField.setColumnComment("分区日期");
        dtField.setDataType("date");
        dtField.setNullable(Boolean.FALSE);
        dtField.setPrimaryKey(Boolean.FALSE);
        dtField.setPartitionKey(Boolean.TRUE);
        dtField.setOrdinalPosition(3);
        dtField.setFieldRole("PARTITION_KEY");
        dtField.setEnabled(Boolean.TRUE);
        dtField.setRemark("模板示例：分区字段");
        sample.getFields().add(dtField);

        IndexRow indexRow = new IndexRow();
        indexRow.setTableName("employee_profile");
        indexRow.setIndexName("idx_employee_profile_name");
        indexRow.setIndexType("BTREE");
        indexRow.setUniqueFlag(Boolean.FALSE);
        indexRow.setPrimaryFlag(Boolean.FALSE);
        indexRow.setColumnName("employee_name");
        indexRow.setColumnOrder(1);
        indexRow.setEnabled(Boolean.TRUE);
        indexRow.setRemark("模板示例：普通查询索引");
        sample.getIndexes().add(indexRow);

        return sample;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TableRow {
        private String tableName;
        private String tableComment;
        private String tableType;
        private String layerType;
        private Long rowCount;
        private Integer columnCount;
        private String partitionKey;
        private Integer freshnessSeconds;
        private String status;
        private Boolean enabled;
        private String lastScanAt;
        private String lastSyncAt;
        private String remark;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FieldRow {
        private String tableName;
        private String columnName;
        private String columnComment;
        private String dataType;
        private Integer columnLength;
        private Integer columnPrecision;
        private Integer columnScale;
        private Boolean nullable;
        private Boolean primaryKey;
        private Boolean partitionKey;
        private String defaultValue;
        private Integer ordinalPosition;
        private String fieldRole;
        private Boolean enabled;
        private String remark;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndexRow {
        private String tableName;
        private String indexName;
        private String indexType;
        private Boolean uniqueFlag;
        private Boolean primaryFlag;
        private String columnName;
        private Integer columnOrder;
        private Boolean enabled;
        private String remark;
    }
}
