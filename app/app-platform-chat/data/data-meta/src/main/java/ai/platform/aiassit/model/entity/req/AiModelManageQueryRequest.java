package ai.platform.aiassit.model.entity.req;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiModelManageQueryRequest extends BaseRequest {

    private String keyword;

    private AiChatClientType clientType;

    private String modelCode;

    private Boolean enabled;
}
