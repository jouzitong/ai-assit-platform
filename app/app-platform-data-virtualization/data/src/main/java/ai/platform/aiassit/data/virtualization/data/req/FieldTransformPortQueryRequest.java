package ai.platform.aiassit.data.virtualization.data.req;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FieldSide;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class FieldTransformPortQueryRequest extends BaseRequest {
    private Long ruleId;
    private FieldSide fieldSide;
    private Long virtualFieldId;
    private Long physicalFieldMetaId;
}
