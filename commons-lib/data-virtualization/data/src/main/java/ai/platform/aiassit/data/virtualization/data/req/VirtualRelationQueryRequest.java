package ai.platform.aiassit.data.virtualization.data.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class VirtualRelationQueryRequest extends BaseRequest {
    private String relationCode;
    private Long sourceEntityId;
    private Long targetEntityId;
    private Boolean enabled;
}
