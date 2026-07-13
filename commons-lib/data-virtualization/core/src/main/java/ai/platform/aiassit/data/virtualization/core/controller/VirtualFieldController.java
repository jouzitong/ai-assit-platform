package ai.platform.aiassit.data.virtualization.core.controller;

import ai.platform.aiassit.data.virtualization.data.dto.VirtualFieldDTO;
import ai.platform.aiassit.data.virtualization.data.req.VirtualFieldQueryRequest;
import ai.platform.aiassit.data.virtualization.data.service.VirtualFieldService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/virtual-data/fields")
public class VirtualFieldController extends BaseController<VirtualFieldDTO, VirtualFieldQueryRequest, VirtualFieldService> {
    private final VirtualFieldService service;

    public VirtualFieldController(VirtualFieldService service) {
        this.service = service;
    }

    @Override
    protected VirtualFieldService service() {
        return service;
    }
}
