package ai.platform.aiassit.data.virtualization.data.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class VirtualBindingQueryRequest extends BaseRequest {
    private Long entityId;
    private String bindingCode;
    private String sourceKey;
    private Boolean enabled;
}
