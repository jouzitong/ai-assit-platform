package ai.platform.aiassit.db.engine.meta.entity.excel;

import lombok.Data;

/**
 * 字段说明 sheet 行。
 */
@Data
public class DbTableFieldMetaExcelRow {

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
