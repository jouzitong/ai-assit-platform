package ai.platform.aiassit.data.virtualization.data.dto;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RelationResultMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class VirtualRelationDTO extends AuditableDTO {
    private String relationCode;
    private String relationName;
    private RelationResultMode resultMode = RelationResultMode.OBJECT;
    private Long sourceEntityId;
    private Long sourceFieldId;
    private Long targetEntityId;
    private Long targetFieldId;
    private Boolean enabled;
    private String remark;
}
