package ai.platform.aiassit.data.virtualization.core.controller;

import ai.platform.aiassit.data.virtualization.data.dto.FieldTransformPortDTO;
import ai.platform.aiassit.data.virtualization.data.req.FieldTransformPortQueryRequest;
import ai.platform.aiassit.data.virtualization.data.service.FieldTransformPortService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/virtual-data/field-transform-ports")
public class FieldTransformPortController extends BaseController<FieldTransformPortDTO, FieldTransformPortQueryRequest, FieldTransformPortService> {
    private final FieldTransformPortService service;
    public FieldTransformPortController(FieldTransformPortService service) { this.service = service; }
    @Override protected FieldTransformPortService service() { return service; }
}
