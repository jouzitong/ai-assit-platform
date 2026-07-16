package ai.platform.aiassit.service.ai.api;

import ai.platform.aiassit.service.ai.api.dto.HybridSearchRequest;
import ai.platform.aiassit.service.ai.api.dto.HybridSearchResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AI 检索执行 API（HTTP/Feign）。
 */
@FeignClient(
        name = "chat",
        contextId = "platformChatRetrievalClient",
        path = "/chat")
@Deprecated // AiKnowledgeApi
public interface AiRetrievalExecutionApi {

    @PostMapping("/api/v1/ai/execution/retrieval/hybrid-search")
    HybridSearchResponse hybridSearch(@RequestBody HybridSearchRequest request);

}
