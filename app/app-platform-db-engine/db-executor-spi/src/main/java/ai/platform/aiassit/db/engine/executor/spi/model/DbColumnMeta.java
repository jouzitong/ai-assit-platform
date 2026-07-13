package ai.platform.aiassit.db.engine.executor.spi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbColumnMeta {

    private String tableName;

    private String columnName;

    private String dataType;

    private Integer columnLength;

    private Integer columnPrecision;

    private Integer columnScale;

    private Boolean nullable;

    private Boolean primaryKey;

    private String defaultValue;

    private Integer ordinalPosition;

    private String columnComment;

    private Boolean autoIncrement;
}
