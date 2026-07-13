package ai.platform.aiassit.data.virtualization.core.controller;

import ai.platform.aiassit.data.virtualization.data.dto.VirtualBindingDTO;
import ai.platform.aiassit.data.virtualization.data.req.VirtualBindingQueryRequest;
import ai.platform.aiassit.data.virtualization.data.service.VirtualBindingService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/virtual-data/bindings")
public class VirtualBindingController extends BaseController<VirtualBindingDTO, VirtualBindingQueryRequest, VirtualBindingService> {
    private final VirtualBindingService service;
    public VirtualBindingController(VirtualBindingService service) { this.service = service; }
    @Override protected VirtualBindingService service() { return service; }
}
