package ai.platform.aiassist.service.ai.core;

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
import ai.platform.aiassist.service.ai.api.stream.ChatStreamObserver;

/**
 * AI 执行领域服务。
 */
public interface AiExecutionDomainService {

    ChatResponse chat(ChatRequest request);

    void chatStream(ChatRequest request, ChatStreamObserver observer);

    void chatStreamAsync(ChatRequest request, ChatStreamObserver observer);

    EmbedResponse embed(EmbedRequest request);

    RerankResponse rerank(RerankRequest request);

    KbUpsertResponse kbUpsert(KbUpsertRequest request);

    KbDeleteResponse kbDelete(KbDeleteRequest request);

    KbSearchResponse kbSearch(KbSearchRequest request);
}
