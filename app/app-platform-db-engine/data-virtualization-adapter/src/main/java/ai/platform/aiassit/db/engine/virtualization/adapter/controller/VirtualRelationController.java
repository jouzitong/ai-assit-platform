package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.data.virtualization.data.dto.VirtualRelationDTO;
import ai.platform.aiassit.data.virtualization.data.req.VirtualRelationQueryRequest;
import ai.platform.aiassit.data.virtualization.data.service.VirtualRelationService;
import ai.platform.aiassit.data.virtualization.core.relation.VirtualRelationBatchSaveRequest;
import ai.platform.aiassit.data.virtualization.core.relation.VirtualRelationBatchSaveResponse;
import ai.platform.aiassit.data.virtualization.core.relation.VirtualRelationManagementService;
import ai.platform.aiassit.data.virtualization.core.relation.VirtualRelationSuggestRequest;
import ai.platform.aiassit.data.virtualization.core.relation.VirtualRelationSuggestion;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 虚拟实体关系维护与关系建议接口。
 *
 * <p>复用 {@link BaseController} 管理单条关系记录，并提供批量保存和基于已知元数据的关系建议，供目录发布和查询关联解析使用。</p>
 */
@RestController
@RequestMapping("/api/v1/virtual-data/relations")
public class VirtualRelationController extends BaseController<VirtualRelationDTO, VirtualRelationQueryRequest, VirtualRelationService> {
    private final VirtualRelationService service;
    private final VirtualRelationManagementService managementService;

    public VirtualRelationController(VirtualRelationService service, VirtualRelationManagementService managementService) {
        this.service = service;
        this.managementService = managementService;
    }

    @Override protected VirtualRelationService service() { return service; }

    /**
     * 原子化保存一组虚拟实体关系。
     *
     * @param request 批量保存请求体，包含实体、字段、关系类型及待新增/更新/删除项
     * @return 批量保存结果，包含各关系的最终状态和校验信息
     */
    @PostMapping("/batch-save")
    public VirtualRelationBatchSaveResponse saveBatch(@RequestBody VirtualRelationBatchSaveRequest request) {
        return managementService.saveBatch(request);
    }

    /**
     * 根据虚拟实体和字段上下文生成候选关系建议。
     *
     * @param request 关系建议请求体，包含待分析的实体、字段及可选约束
     * @return 候选关联列表，供开发者审核后再保存，不会直接发布目录
     */
    @PostMapping("/ai-suggest")
    public List<VirtualRelationSuggestion> suggest(@RequestBody VirtualRelationSuggestRequest request) {
        return managementService.suggest(request);
    }
}
