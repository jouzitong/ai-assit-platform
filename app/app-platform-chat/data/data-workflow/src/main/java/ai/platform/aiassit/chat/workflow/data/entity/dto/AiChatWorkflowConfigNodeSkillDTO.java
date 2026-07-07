package ai.platform.aiassit.chat.workflow.data.entity.dto;

import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowNodeSkillRuntimeConfig;
import ai.platform.aiassit.chat.workflow.data.enums.WorkflowNodeSkillPhase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatWorkflowConfigNodeSkillDTO extends BaseDTO {

    private String configCode;

    private String nodeCode;

    private String skillCode;

    private WorkflowNodeSkillPhase phase;

    private Integer sort;

    private Boolean enabled = Boolean.TRUE;

    private WorkflowNodeSkillRuntimeConfig config;
}
