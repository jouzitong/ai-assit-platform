package ai.platform.aiassist.service.ai.api;

import ai.platform.aiassist.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassist.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassist.service.ai.api.dto.RerankRequest;
import ai.platform.aiassist.service.ai.api.dto.RerankResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AI 向量执行 API（HTTP/Feign）。
 */
@FeignClient(
        name = "${app.ai-engine.name:boot-ai-engine}",
        url = "${app.ai-engine.url:http://127.0.0.1:13101}"
)
public interface AiVectorExecutionApi {

    @PostMapping("/api/v1/ai/execution/vector/embed")
    EmbedResponse embed(@RequestBody EmbedRequest request);

    @PostMapping("/api/v1/ai/execution/vector/rerank")
    RerankResponse rerank(@RequestBody RerankRequest request);
}
