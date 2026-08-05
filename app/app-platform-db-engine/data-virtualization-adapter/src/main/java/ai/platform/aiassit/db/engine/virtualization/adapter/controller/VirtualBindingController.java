package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.data.virtualization.data.dto.VirtualBindingDTO;
import ai.platform.aiassit.data.virtualization.data.req.VirtualBindingQueryRequest;
import ai.platform.aiassit.data.virtualization.data.service.VirtualBindingService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 虚拟目录与物理数据源绑定关系的通用 CRUD 接口。
 *
 * <p>复用 {@link BaseController} 维护虚拟实体、物理表及数据源之间的映射；请求体使用 {@link VirtualBindingDTO}，查询条件使用
 * {@link VirtualBindingQueryRequest}，供查询路由和发布流程解析真实数据来源。</p>
 */
@RestController
@RequestMapping("/api/v1/virtual-data/bindings")
public class VirtualBindingController extends BaseController<VirtualBindingDTO, VirtualBindingQueryRequest, VirtualBindingService> {
    private final VirtualBindingService service;
    public VirtualBindingController(VirtualBindingService service) { this.service = service; }
    @Override protected VirtualBindingService service() { return service; }
}
