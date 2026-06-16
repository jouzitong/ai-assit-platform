package ai.platform.aiassit.chat.workflow.data.entity.req;

import ai.platform.aiassit.chat.workflow.data.enums.WorkflowNodeSkillPhase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatWorkflowConfigNodeSkillQueryRequest extends BaseRequest {

    private String configCode;

    private String nodeCode;

    private String skillCode;

    private WorkflowNodeSkillPhase phase;

    private Boolean enabled;
}
