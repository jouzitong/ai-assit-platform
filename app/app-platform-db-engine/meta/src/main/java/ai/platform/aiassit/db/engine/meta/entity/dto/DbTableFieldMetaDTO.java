package ai.platform.aiassit.db.engine.meta.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbTableFieldMetaDTO extends BaseDTO {

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
