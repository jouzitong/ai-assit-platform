package ai.platform.aiassist.service.ai.kb.entity.req;

import ai.platform.aiassist.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassist.service.ai.api.enums.AiKbStoreStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbStoreQueryRequest extends BaseRequest {

    private String kbCode;

    private String bizKey;

    private AiKbBizType bizType;

    private AiKbStoreStatus status;

    private Boolean enabled;
}
