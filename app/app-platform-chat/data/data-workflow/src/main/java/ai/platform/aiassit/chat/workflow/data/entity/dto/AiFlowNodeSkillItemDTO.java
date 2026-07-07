package ai.platform.aiassit.chat.workflow.data.entity.dto;

import ai.platform.aiassit.chat.workflow.data.enums.WorkflowNodeSkillPhase;
import lombok.Data;

@Data
public class AiFlowNodeSkillItemDTO {

    private Long id;

    private String key;

    private String name;

    private WorkflowNodeSkillPhase phase;

    private String status;

    private String summary;
}
