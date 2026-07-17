package ai.platform.aiassit.chat.agent.control.data.controller;

import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.WorkflowControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.service.control.AiWorkflowControlService;
import jakarta.validation.Valid;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/workflows")
public class AiArtifactWorkflowManageController {

    private final AiWorkflowControlService service;

    public AiArtifactWorkflowManageController(AiWorkflowControlService service) {
        this.service = service;
    }

    @GetMapping
    public R<List<WorkflowControlDTOs.Catalog>> catalogs() {
        return R.ok(service.listCatalogs());
    }

    @GetMapping("/{workflowCode}")
    public R<WorkflowControlDTOs.Version> workflow(@PathVariable String workflowCode) {
        return R.ok(service.getWorkflow(workflowCode));
    }

    @PostMapping
    public R<WorkflowControlDTOs.Version> create(
            @Valid @RequestBody WorkflowControlDTOs.DraftRequest request) {
        return R.ok(service.createDraft(request));
    }

    @PutMapping("/{workflowCode}")
    public R<WorkflowControlDTOs.Version> update(@PathVariable String workflowCode,
                                                 @Valid @RequestBody WorkflowControlDTOs.DraftRequest request) {
        return R.ok(service.updateWorkflow(workflowCode, request));
    }

    @DeleteMapping("/{workflowCode}")
    public R<Boolean> delete(@PathVariable String workflowCode) {
        return R.ok(service.deleteWorkflow(workflowCode));
    }

    @PostMapping("/{workflowCode}/versions")
    public R<WorkflowControlDTOs.Version> createVersion(@PathVariable String workflowCode,
                                                        @Valid @RequestBody WorkflowControlDTOs.DraftRequest request) {
        return R.ok(service.createVersion(workflowCode, request));
    }

    @GetMapping("/{workflowCode}/versions")
    public R<List<WorkflowControlDTOs.Version>> versions(@PathVariable String workflowCode) {
        return R.ok(service.listVersions(workflowCode));
    }

    @GetMapping("/{workflowCode}/versions/{version}")
    public R<WorkflowControlDTOs.Version> version(@PathVariable String workflowCode,
                                                  @PathVariable Integer version) {
        return R.ok(service.getVersion(workflowCode, version));
    }

    @PostMapping("/{workflowCode}/versions/{version}/validate")
    public R<ValidationReportDTO> validate(@PathVariable String workflowCode,
                                           @PathVariable Integer version) {
        return R.ok(service.validateVersion(workflowCode, version));
    }

    @PostMapping("/{workflowCode}/versions/{version}/publish")
    public R<WorkflowControlDTOs.Version> publish(@PathVariable String workflowCode,
                                                  @PathVariable Integer version) {
        return R.ok(service.publishVersion(workflowCode, version));
    }

    @PostMapping("/{workflowCode}/versions/{version}/test-runs")
    public R<Map<String, Object>> test(@PathVariable String workflowCode,
                                       @PathVariable Integer version,
                                       @RequestBody(required = false) Map<String, Object> payload) {
        return R.ok(service.testVersion(workflowCode, version, payload == null ? Map.of() : payload));
    }
}
