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

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TableRow {
        private String sourceKey;
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
        private String sourceKey;
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
        private String sourceKey;
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
