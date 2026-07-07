package ai.platform.aiassit.chat.workflow.data.entity.dto;

import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowNodeRuntimeConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatWorkflowConfigNodeDTO extends BaseDTO {

    private String configCode;

    private String nodeCode;

    private Integer sort;

    private String nextCode;

    private Boolean enabled = Boolean.TRUE;

    private WorkflowNodeRuntimeConfig config;
}
