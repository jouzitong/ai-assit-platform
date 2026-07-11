package ai.platform.aiassit.db.engine.executor.spi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据源返回的索引元数据。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbIndexMeta {

    private String tableName;

    private String indexName;

    private String indexType;

    private Boolean uniqueFlag;

    private Boolean primaryFlag;

    private String columnName;

    private Integer columnOrder;
}
