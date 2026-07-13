package ai.platform.aiassit.data.virtualization.data.dto;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.LogicalType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class VirtualFieldDTO extends AuditableDTO {
    private Long entityId;
    private String fieldCode;
    private String fieldName;
    private LogicalType logicalType;
    private Boolean nullable;
    private Boolean primaryKey;
    private Integer ordinalPosition;
    private String defaultValue;
    private Boolean enabled;
    private String remark;
}
