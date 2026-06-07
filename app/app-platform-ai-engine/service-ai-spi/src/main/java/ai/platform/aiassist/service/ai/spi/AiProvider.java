package ai.platform.aiassist.service.ai.spi;

import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.RerankResponse;
import ai.platform.aiassist.service.ai.api.enums.ProviderType;
import ai.platform.aiassist.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderChatRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderEmbedRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderKbDeleteRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderKbSearchRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderKbUpsertRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderRerankRequest;

/**
 * AI 提供方 SPI。
 *
 * <p>SPI 仅定义模型基础能力和统一入参，不承载业务编排逻辑。</p>
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
