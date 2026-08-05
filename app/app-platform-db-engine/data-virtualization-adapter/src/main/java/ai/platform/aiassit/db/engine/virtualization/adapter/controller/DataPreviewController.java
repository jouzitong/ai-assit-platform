package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.db.engine.api.DataPreviewApi;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryRequest;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryResponse;
import ai.platform.aiassit.db.engine.virtualization.adapter.service.DataPreviewApplicationService;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent Tool 使用的受控数据预览内部入口。
 *
 * <p>只接受虚拟模型和虚拟字段语义，不接受物理表、数据源、SQL 或底层执行参数，从接口层保持 Agent 的最小数据访问边界。</p>
 */
@RestController
@RequestMapping
public class DataPreviewController implements DataPreviewApi {

    private final DataPreviewApplicationService service;

    public DataPreviewController(DataPreviewApplicationService service) {
        this.service = service;
    }

    /**
     * 在虚拟数据权限和字段策略约束下预览数据。
     *
     * @param request 预览请求体，包含虚拟实体、字段选择、筛选语义和返回限制
     * @return 包装后的预览结果，包含允许返回的列、记录和受控执行元数据
     */
    @Override
    @PostMapping("/internal/v1/data-preview/query")
    public R<DataPreviewQueryResponse> query(@RequestBody DataPreviewQueryRequest request) {
        return R.ok(service.query(request));
    }
}
