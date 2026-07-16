package ai.platform.aiassit.chat.workflow.data.service.control;

import ai.platform.aiassit.chat.workflow.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.workflow.data.entity.dto.control.WorkflowControlDTOs;

import java.util.List;

public interface AiWorkflowControlService {

    List<WorkflowControlDTOs.Catalog> listCatalogs();

    WorkflowControlDTOs.Version getWorkflow(String workflowCode);

    WorkflowControlDTOs.Version createDraft(WorkflowControlDTOs.DraftRequest request);

    WorkflowControlDTOs.Version createVersion(String workflowCode, WorkflowControlDTOs.DraftRequest request);

    WorkflowControlDTOs.Version updateWorkflow(String workflowCode, WorkflowControlDTOs.DraftRequest request);

    boolean deleteWorkflow(String workflowCode);

    List<WorkflowControlDTOs.Version> listVersions(String workflowCode);

    WorkflowControlDTOs.Version getVersion(String workflowCode, Integer version);

    ValidationReportDTO validateVersion(String workflowCode, Integer version);

    WorkflowControlDTOs.Version publishVersion(String workflowCode, Integer version);

    java.util.Map<String, Object> testVersion(String workflowCode, Integer version,
                                              java.util.Map<String, Object> payload);
}
