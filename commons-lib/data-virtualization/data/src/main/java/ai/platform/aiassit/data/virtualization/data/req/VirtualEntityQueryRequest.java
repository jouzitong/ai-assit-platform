package ai.platform.aiassit.data.virtualization.data.req;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.CatalogStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class VirtualEntityQueryRequest extends BaseRequest {
    private String entityCode;
    private CatalogStatus status;
    private Boolean enabled;
}
