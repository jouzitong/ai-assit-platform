package ai.platform.aiassit.agent.controller;

import ai.platform.aiassit.agent.runtime.tool.AiAgentPlatformToolFacadeService;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryRequest;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryResponse;
import ai.platform.aiassit.service.ai.api.AiAgentPlatformToolInternalApi;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地 Agent Worker 调用的平台工具中转接口。
 *
 * <p>Worker 只访问 Chat 服务；Chat 在此处保留运行链路、请求校验和审计边界后，再同步委托数据平台执行受控操作。</p>
 */
@RestController
public class AiAgentPlatformToolInternalController implements AiAgentPlatformToolInternalApi {

    private final AiAgentPlatformToolFacadeService facadeService;

    public AiAgentPlatformToolInternalController(AiAgentPlatformToolFacadeService facadeService) {
        this.facadeService = facadeService;
    }

    /**
     * 以 Agent 运行上下文执行数据预览查询。
     *
     * @param runId   可选的 Agent 运行标识，用于审计和执行归属
     * @param traceId 可选的链路追踪标识
     * @param request 可选请求体，包含数据源、查询语句和预览限制
     * @return 受平台权限与策略约束后的数据预览结果
     */
    @Override
    public R<DataPreviewQueryResponse> queryDataPreview(
            @RequestHeader(value = "X-Agent-Run-Id", required = false) String runId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody(required = false) DataPreviewQueryRequest request
    ) {
        return R.ok(facadeService.queryDataPreview(runId, traceId, request));
    }
}
