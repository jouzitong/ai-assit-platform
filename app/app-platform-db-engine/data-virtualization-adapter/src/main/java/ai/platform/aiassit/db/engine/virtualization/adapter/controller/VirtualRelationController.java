package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.data.virtualization.data.dto.VirtualRelationDTO;
import ai.platform.aiassit.data.virtualization.data.req.VirtualRelationQueryRequest;
import ai.platform.aiassit.data.virtualization.data.service.VirtualRelationService;
import ai.platform.aiassit.data.virtualization.core.relation.VirtualRelationBatchSaveRequest;
import ai.platform.aiassit.data.virtualization.core.relation.VirtualRelationBatchSaveResponse;
import ai.platform.aiassit.data.virtualization.core.relation.VirtualRelationManagementService;
import ai.platform.aiassit.data.virtualization.core.relation.VirtualRelationSuggestRequest;
import ai.platform.aiassit.data.virtualization.core.relation.VirtualRelationSuggestion;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/virtual-data/relations")
public class VirtualRelationController extends BaseController<VirtualRelationDTO, VirtualRelationQueryRequest, VirtualRelationService> {
    private final VirtualRelationService service;
    private final VirtualRelationManagementService managementService;

    public VirtualRelationController(VirtualRelationService service, VirtualRelationManagementService managementService) {
        this.service = service;
        this.managementService = managementService;
    }

    @Override protected VirtualRelationService service() { return service; }

    @PostMapping("/batch-save")
    public VirtualRelationBatchSaveResponse saveBatch(@RequestBody VirtualRelationBatchSaveRequest request) {
        return managementService.saveBatch(request);
    }

    @PostMapping("/ai-suggest")
    public List<VirtualRelationSuggestion> suggest(@RequestBody VirtualRelationSuggestRequest request) {
        return managementService.suggest(request);
    }
}
