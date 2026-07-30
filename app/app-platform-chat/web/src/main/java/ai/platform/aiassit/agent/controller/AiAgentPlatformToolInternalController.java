package ai.platform.aiassit.agent.controller;

import ai.platform.aiassit.agent.runtime.tool.AiAgentPlatformToolFacadeService;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryRequest;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryResponse;
import ai.platform.aiassit.service.ai.api.AiAgentPlatformToolInternalApi;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Local Agent worker entrypoint for Chat-owned platform Tool mediation. */
@RestController
public class AiAgentPlatformToolInternalController implements AiAgentPlatformToolInternalApi {

    private final AiAgentPlatformToolFacadeService facadeService;

    public AiAgentPlatformToolInternalController(AiAgentPlatformToolFacadeService facadeService) {
        this.facadeService = facadeService;
    }

    @Override
    public R<DataPreviewQueryResponse> queryDataPreview(
            @RequestHeader(value = "X-Agent-Run-Id", required = false) String runId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody(required = false) DataPreviewQueryRequest request
    ) {
        return R.ok(facadeService.queryDataPreview(runId, traceId, request));
    }
}
