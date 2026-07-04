package ai.platform.aiassit.chat.workflow.data.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatWorkflowQueryRequest extends BaseRequest {

    private String code;

    private String name;

    private String type;

    private Boolean enabled;
}
