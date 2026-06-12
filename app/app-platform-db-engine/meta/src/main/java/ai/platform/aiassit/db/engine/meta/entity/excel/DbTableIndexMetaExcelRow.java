package ai.platform.aiassit.db.engine.meta.entity.excel;

import lombok.Data;

/**
 * 索引说明 sheet 行。
 */
@Data
public class DbTableIndexMetaExcelRow {

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
