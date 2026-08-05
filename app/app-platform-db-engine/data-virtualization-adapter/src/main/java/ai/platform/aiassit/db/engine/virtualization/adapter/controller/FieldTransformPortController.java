package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.data.virtualization.data.dto.FieldTransformPortDTO;
import ai.platform.aiassit.data.virtualization.data.req.FieldTransformPortQueryRequest;
import ai.platform.aiassit.data.virtualization.data.service.FieldTransformPortService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字段转换器接入端口的通用 CRUD 接口。
 *
 * <p>复用 {@link BaseController} 维护可被虚拟字段转换规则引用的转换器端口；请求体使用 {@link FieldTransformPortDTO}，查询条件使用
 * {@link FieldTransformPortQueryRequest}，响应返回端口能力与启用状态。</p>
 */
@RestController
@RequestMapping("/api/v1/virtual-data/field-transform-ports")
public class FieldTransformPortController extends BaseController<FieldTransformPortDTO, FieldTransformPortQueryRequest, FieldTransformPortService> {
    private final FieldTransformPortService service;
    public FieldTransformPortController(FieldTransformPortService service) { this.service = service; }
    @Override protected FieldTransformPortService service() { return service; }
}
