package ai.platform.aiassit.db.engine.meta.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbTableIndexMetaDTO extends BaseDTO {

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
