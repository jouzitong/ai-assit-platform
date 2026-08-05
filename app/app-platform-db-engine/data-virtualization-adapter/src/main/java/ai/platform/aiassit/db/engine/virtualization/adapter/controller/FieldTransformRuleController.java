package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.data.virtualization.data.dto.FieldTransformRuleDTO;
import ai.platform.aiassit.data.virtualization.data.req.FieldTransformRuleQueryRequest;
import ai.platform.aiassit.data.virtualization.data.service.FieldTransformRuleService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 虚拟字段转换规则的通用 CRUD 接口。
 *
 * <p>复用 {@link BaseController} 维护字段清洗、映射和脚本化转换规则；请求体使用 {@link FieldTransformRuleDTO}，查询条件使用
 * {@link FieldTransformRuleQueryRequest}，规则校验和预览由目录管理接口提供。</p>
 */
@RestController
@RequestMapping("/api/v1/virtual-data/field-transform-rules")
public class FieldTransformRuleController extends BaseController<FieldTransformRuleDTO, FieldTransformRuleQueryRequest, FieldTransformRuleService> {
    private final FieldTransformRuleService service;
    public FieldTransformRuleController(FieldTransformRuleService service) { this.service = service; }
    @Override protected FieldTransformRuleService service() { return service; }
}
