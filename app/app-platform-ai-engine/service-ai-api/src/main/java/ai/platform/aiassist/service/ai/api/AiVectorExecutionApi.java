package ai.platform.aiassist.service.ai.api;

import ai.platform.aiassist.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassist.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassist.service.ai.api.dto.RerankRequest;
import ai.platform.aiassist.service.ai.api.dto.RerankResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * AI 向量执行 API（HTTP/Feign）。
 */
@FeignClient(
        name = "${app.ai-engine.name:boot-ai-engine}",
        url = "${app.ai-engine.url:http://127.0.0.1:13101}"
)
@RequestMapping("/api/v1/ai/execution")
public interface AiVectorExecutionApi {

    @PostMapping("/vector/embed")
    EmbedResponse embed(@RequestBody EmbedRequest request);

    @PostMapping("/vector/rerank")
    RerankResponse rerank(@RequestBody RerankRequest request);
}
