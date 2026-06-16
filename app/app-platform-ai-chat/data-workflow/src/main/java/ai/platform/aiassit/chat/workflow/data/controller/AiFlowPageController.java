package ai.platform.aiassit.chat.workflow.data.controller;

import ai.platform.aiassit.chat.workflow.data.entity.dto.AiFlowDetailDTO;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiFlowOverviewDTO;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiFlowWorkflowFormDTO;
import ai.platform.aiassit.chat.workflow.data.service.AiFlowPageService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat/workflow/internal/page")
public class AiFlowPageController {

    private final AiFlowPageService service;

    public AiFlowPageController(AiFlowPageService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public AiFlowOverviewDTO overview() {
        return service.overview();
    }

    @GetMapping("/detail/{workflowKey}")
    public AiFlowDetailDTO detail(@PathVariable("workflowKey") String workflowKey) {
        return service.detail(workflowKey);
    }

    @PostMapping("/workflow")
    public AiFlowDetailDTO saveWorkflow(@RequestBody AiFlowWorkflowFormDTO form) {
        return service.saveWorkflow(form);
    }

    @PutMapping("/workflow/{id}")
    public AiFlowDetailDTO updateWorkflow(@PathVariable("id") Long id, @RequestBody AiFlowWorkflowFormDTO form) {
        return service.updateWorkflow(id, form);
    }

    @DeleteMapping("/workflow/{id}")
    public Boolean deleteWorkflow(@PathVariable("id") Long id) {
        return service.deleteWorkflow(id);
    }
}
