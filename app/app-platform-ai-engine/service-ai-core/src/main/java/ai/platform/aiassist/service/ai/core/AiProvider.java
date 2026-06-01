package ai.platform.aiassist.service.ai.core;

import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.RerankResponse;
import ai.platform.aiassist.service.ai.api.enums.ProviderType;
import ai.platform.aiassist.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassist.service.ai.core.provider.dto.ProviderChatRequest;
import ai.platform.aiassist.service.ai.core.provider.dto.ProviderEmbedRequest;
import ai.platform.aiassist.service.ai.core.provider.dto.ProviderKbDeleteRequest;
import ai.platform.aiassist.service.ai.core.provider.dto.ProviderKbSearchRequest;
import ai.platform.aiassist.service.ai.core.provider.dto.ProviderKbUpsertRequest;
import ai.platform.aiassist.service.ai.core.provider.dto.ProviderRerankRequest;

/**
 * AI 提供方内部 SPI。
 *
 * <p>Provider 只面向 core 层规范化后的请求，不直接依赖业务 API 入参。</p>
 */
public interface AiProvider {

    ProviderType providerType();

    ChatResponse chat(ProviderChatRequest request);

    void chatStream(ProviderChatRequest request, ChatStreamObserver observer);

    EmbedResponse embed(ProviderEmbedRequest request);

    RerankResponse rerank(ProviderRerankRequest request);

    KbUpsertResponse kbUpsert(ProviderKbUpsertRequest request);

    KbDeleteResponse kbDelete(ProviderKbDeleteRequest request);

    KbSearchResponse kbSearch(ProviderKbSearchRequest request);
}
