package ai.platform.aiassit.data.virtualization.core.catalog;

import lombok.Data;

@Data
public class CreateVirtualEntityFromTableRequest {
    private Long physicalTableMetaId;
    private String entityCode;
    private String entityName;
}
