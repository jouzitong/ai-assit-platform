package ai.platform.aiassist.service.ai.meta.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiMetaQueryRequest extends BaseRequest {

    private String providerCode;

    private String modelCode;

    private Boolean enabled;
}
