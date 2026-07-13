package ai.platform.aiassit.data.virtualization.data.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class FieldTransformRuleQueryRequest extends BaseRequest {
    private Long bindingId;
    private String ruleCode;
    private Boolean enabled;
}
