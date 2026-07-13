package ai.platform.aiassit.data.virtualization.data.dto;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FieldSide;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class FieldTransformPortDTO extends AuditableDTO {
    private Long ruleId;
    private FieldSide fieldSide;
    private String portCode;
    private Long virtualFieldId;
    private Long physicalFieldMetaId;
    private String physicalColumnName;
    private Integer ordinalPosition;
    private Boolean requiredOnWrite;
    private String remark;
}
