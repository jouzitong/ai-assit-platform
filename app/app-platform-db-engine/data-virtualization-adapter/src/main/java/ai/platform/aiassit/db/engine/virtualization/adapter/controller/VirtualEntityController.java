package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.data.virtualization.data.dto.VirtualEntityDTO;
import ai.platform.aiassit.data.virtualization.data.req.VirtualEntityQueryRequest;
import ai.platform.aiassit.data.virtualization.data.service.VirtualEntityService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 虚拟业务实体的通用 CRUD 接口。
 *
 * <p>复用 {@link BaseController} 维护面向业务的逻辑实体，而非直接暴露物理表；请求体使用 {@link VirtualEntityDTO}，查询条件使用
 * {@link VirtualEntityQueryRequest}，发布后可供受控数据查询和知识库描述使用。</p>
 */
@RestController
@RequestMapping("/api/v1/virtual-data/entities")
public class VirtualEntityController extends BaseController<VirtualEntityDTO, VirtualEntityQueryRequest, VirtualEntityService> {
    private final VirtualEntityService service;
    public VirtualEntityController(VirtualEntityService service) { this.service = service; }
    @Override protected VirtualEntityService service() { return service; }
}
