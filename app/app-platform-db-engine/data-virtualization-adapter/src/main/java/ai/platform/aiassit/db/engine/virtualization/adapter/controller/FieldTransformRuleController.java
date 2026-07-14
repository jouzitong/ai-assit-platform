package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.data.virtualization.data.dto.FieldTransformRuleDTO;
import ai.platform.aiassit.data.virtualization.data.req.FieldTransformRuleQueryRequest;
import ai.platform.aiassit.data.virtualization.data.service.FieldTransformRuleService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/virtual-data/field-transform-rules")
public class FieldTransformRuleController extends BaseController<FieldTransformRuleDTO, FieldTransformRuleQueryRequest, FieldTransformRuleService> {
    private final FieldTransformRuleService service;
    public FieldTransformRuleController(FieldTransformRuleService service) { this.service = service; }
    @Override protected FieldTransformRuleService service() { return service; }
}
