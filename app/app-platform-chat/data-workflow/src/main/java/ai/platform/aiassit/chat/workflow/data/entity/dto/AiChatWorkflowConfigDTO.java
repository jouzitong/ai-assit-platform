package ai.platform.aiassit.chat.workflow.data.entity.dto;

import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowRuntimeConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatWorkflowConfigDTO extends BaseDTO {

    private String code;

    private String workflowCode;

    private String name;

    private Boolean enabled = Boolean.TRUE;

    private WorkflowRuntimeConfig config;
}
