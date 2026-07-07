package ai.platform.aiassit.chat.workflow.data.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatWorkflowConfigNodeQueryRequest extends BaseRequest {

    private String configCode;

    private String nodeCode;

    private String nextCode;

    private Boolean enabled;
}
