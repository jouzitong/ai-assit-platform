package ai.platform.aiassit.chat.workflow.data.service;

import ai.platform.aiassit.chat.workflow.data.entity.dto.AiFlowDetailDTO;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiFlowOverviewDTO;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiFlowWorkflowFormDTO;

public interface AiFlowPageService {

    AiFlowOverviewDTO overview();

    AiFlowDetailDTO detail(String workflowKey);

    AiFlowDetailDTO saveWorkflow(AiFlowWorkflowFormDTO form);

    AiFlowDetailDTO updateWorkflow(Long id, AiFlowWorkflowFormDTO form);

    Boolean deleteWorkflow(Long id);
}
