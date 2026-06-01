package ai.platform.aiassist.service.ai.api;

import ai.platform.aiassist.service.ai.api.chat.AiChatApi;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassist.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.RerankRequest;
import ai.platform.aiassist.service.ai.api.dto.RerankResponse;
import ai.platform.aiassist.service.ai.api.knowledge.AiKnowledgeBaseApi;
import ai.platform.aiassist.service.ai.api.vector.AiVectorApi;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * AI 执行引擎统一聚合 API（兼容入口）。
 *
 * <p>该接口只做能力聚合，便于历史代码兼容；
 * 新业务建议按领域优先依赖以下接口：</p>
 *
 * <p>1) 对话能力：{@link AiChatApi}<br>
 * 2) 知识库能力：{@link AiKnowledgeBaseApi}<br>
 * 3) 向量与重排能力：{@link AiVectorApi}</p>
 */
@FeignClient(
        name = "${app.ai-engine.name:boot-ai-engine}",
        url = "${app.ai-engine.url:http://127.0.0.1:13101}"
)
@RequestMapping("/api/v1/ai/execution")
public interface AiExecutionApi extends AiKnowledgeBaseApi, AiVectorApi {

    @PostMapping("/chat")
    ChatResponse chat(@RequestBody ChatRequest request);

    @Override
    @PostMapping("/vector/embed")
    EmbedResponse embed(@RequestBody EmbedRequest request);

    @Override
    @PostMapping("/vector/rerank")
    RerankResponse rerank(@RequestBody RerankRequest request);

    @Override
    @PostMapping("/kb/upsert")
    KbUpsertResponse kbUpsert(@RequestBody KbUpsertRequest request);

    @Override
    @PostMapping("/kb/delete")
    KbDeleteResponse kbDelete(@RequestBody KbDeleteRequest request);

    @Override
    @PostMapping("/kb/search")
    KbSearchResponse kbSearch(@RequestBody KbSearchRequest request);
}
