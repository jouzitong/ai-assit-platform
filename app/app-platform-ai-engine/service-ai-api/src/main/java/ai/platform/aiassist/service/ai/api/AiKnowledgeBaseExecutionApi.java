package ai.platform.aiassist.service.ai.api;

import ai.platform.aiassist.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AI 知识库执行 API（HTTP/Feign）。
 */
@FeignClient(
        name = "${app.ai-engine.name:boot-ai-engine}",
        url = "${app.ai-engine.url:http://127.0.0.1:13101/aiEngine}"
)
public interface AiKnowledgeBaseExecutionApi {

    @PostMapping("/api/v1/ai/execution/kb/upsert")
    KbUpsertResponse kbUpsert(@RequestBody KbUpsertRequest request);

    @PostMapping("/api/v1/ai/execution/kb/delete")
    KbDeleteResponse kbDelete(@RequestBody KbDeleteRequest request);

    @PostMapping("/api/v1/ai/execution/kb/search")
    KbSearchResponse kbSearch(@RequestBody KbSearchRequest request);
}
