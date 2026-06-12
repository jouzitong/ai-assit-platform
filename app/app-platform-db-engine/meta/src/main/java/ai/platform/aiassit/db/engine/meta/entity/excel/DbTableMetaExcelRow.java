package ai.platform.aiassit.db.engine.meta.entity.excel;

import lombok.Data;

/**
 * 表说明 sheet 行。
 */
@Data
public class DbTableMetaExcelRow {

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
