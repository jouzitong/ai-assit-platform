package ai.platform.aiassit.data.virtualization.data.dto;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class VirtualEntityDTO extends AuditableDTO {
    private String entityCode;
    private String entityName;
    private String description;
    private CatalogStatus status;
    private Long catalogVersion;
    private Boolean enabled;
}
