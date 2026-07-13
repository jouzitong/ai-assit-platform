package ai.platform.aiassit.data.virtualization.data.dto;

import ai.platform.aiassit.data.virtualization.api.config.BindingRoutingConfig;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.BindingRole;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class VirtualBindingDTO extends AuditableDTO {
    private Long entityId;
    private String bindingCode;
    private String bindingGroup;
    private BindingRole bindingRole;
    private Long physicalTableMetaId;
    private String sourceKey;
    private String physicalTableName;
    private Boolean readable;
    private Boolean writable;
    private Integer readWeight;
    private Integer writePriority;
    private BindingRoutingConfig routingConfig;
    private Boolean enabled;
    private String remark;
}
