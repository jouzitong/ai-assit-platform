package ai.platform.aiassit.service.ai.api;

import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryRequest;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryResponse;
import org.athena.framework.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Chat-owned internal facade for platform capabilities used by the local Agent worker.
 *
 * <p>The worker only calls Chat. Chat performs its own authentication, request checks
 * and audit logging before synchronously delegating to the owning platform service.</p>
 */
@FeignClient(
        name = "chat",
        contextId = "platformChatAgentPlatformToolInternalClient",
        path = "/chat"
)
public interface AiAgentPlatformToolInternalApi {

    @PostMapping("/internal/v1/ai/agent-tools/data-preview/query")
    R<DataPreviewQueryResponse> queryDataPreview(
            @RequestHeader(value = "X-Agent-Run-Id", required = false) String runId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody(required = false) DataPreviewQueryRequest request
    );
}
