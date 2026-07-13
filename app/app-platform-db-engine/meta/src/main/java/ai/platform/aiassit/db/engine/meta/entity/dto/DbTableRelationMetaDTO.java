package ai.platform.aiassit.db.engine.meta.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class DbTableRelationMetaDTO extends AuditableDTO {

    private String sourceKey;

    private String relationName;

    private String sourceTableName;

    private String sourceColumnName;

    private String targetTableName;

    private String targetColumnName;

    private Boolean enabled;

    private String remark;
}
