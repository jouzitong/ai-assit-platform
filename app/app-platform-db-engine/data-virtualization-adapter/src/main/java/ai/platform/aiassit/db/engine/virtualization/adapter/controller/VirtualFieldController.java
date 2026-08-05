package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.data.virtualization.data.dto.VirtualFieldDTO;
import ai.platform.aiassit.data.virtualization.data.req.VirtualFieldQueryRequest;
import ai.platform.aiassit.data.virtualization.data.service.VirtualFieldService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 虚拟业务字段的通用 CRUD 接口。
 *
 * <p>复用 {@link BaseController} 维护逻辑字段的名称、语义、物理映射和脱敏等属性；请求体使用 {@link VirtualFieldDTO}，查询条件使用
 * {@link VirtualFieldQueryRequest}。</p>
 */
@RestController
@RequestMapping("/api/v1/virtual-data/fields")
public class VirtualFieldController extends BaseController<VirtualFieldDTO, VirtualFieldQueryRequest, VirtualFieldService> {
    private final VirtualFieldService service;
    public VirtualFieldController(VirtualFieldService service) { this.service = service; }
    @Override protected VirtualFieldService service() { return service; }
}
