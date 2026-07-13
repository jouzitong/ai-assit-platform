package ai.platform.aiassit.data.virtualization.core.controller;

import ai.platform.aiassit.data.virtualization.data.dto.VirtualEntityDTO;
import ai.platform.aiassit.data.virtualization.data.req.VirtualEntityQueryRequest;
import ai.platform.aiassit.data.virtualization.data.service.VirtualEntityService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/virtual-data/entities")
public class VirtualEntityController extends BaseController<VirtualEntityDTO, VirtualEntityQueryRequest, VirtualEntityService> {
    private final VirtualEntityService service;
    public VirtualEntityController(VirtualEntityService service) { this.service = service; }
    @Override protected VirtualEntityService service() { return service; }
}
