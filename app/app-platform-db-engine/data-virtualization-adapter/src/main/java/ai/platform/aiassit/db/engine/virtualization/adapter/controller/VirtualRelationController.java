package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.data.virtualization.data.dto.VirtualRelationDTO;
import ai.platform.aiassit.data.virtualization.data.req.VirtualRelationQueryRequest;
import ai.platform.aiassit.data.virtualization.data.service.VirtualRelationService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/virtual-data/relations")
public class VirtualRelationController extends BaseController<VirtualRelationDTO, VirtualRelationQueryRequest, VirtualRelationService> {
    private final VirtualRelationService service;
    public VirtualRelationController(VirtualRelationService service) { this.service = service; }
    @Override protected VirtualRelationService service() { return service; }
}
