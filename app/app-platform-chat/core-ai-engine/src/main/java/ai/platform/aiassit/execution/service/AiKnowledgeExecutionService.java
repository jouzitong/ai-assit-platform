package ai.platform.aiassit.execution.service;

import ai.platform.aiassit.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassit.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassit.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassit.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassit.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.RerankRequest;
import ai.platform.aiassit.service.ai.api.dto.RerankResponse;

/**
 * AI 知识执行领域服务。
 */
public interface AiKnowledgeExecutionService {

    EmbedResponse embed(EmbedRequest request);

    RerankResponse rerank(RerankRequest request);

    KbUpsertResponse kbUpsert(KbUpsertRequest request);

    KbDeleteResponse kbDelete(KbDeleteRequest request);

    KbSearchResponse kbSearch(KbSearchRequest request);
}
