package ai.platform.aiassist.service.ai.core;

import ai.platform.aiassist.service.ai.api.AiExecutionApi;
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
import ai.platform.aiassist.service.ai.api.enums.ProviderType;
import ai.platform.aiassist.service.ai.api.stream.ChatStreamObserver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * AI 统一执行入口默认实现。
 *
 * <p>负责请求校验与提供方路由，不包含具体平台 SDK 细节。</p>
 */
@Service
@Primary
@EnableConfigurationProperties(AiCoreProperties.class)
public class DefaultAiExecutionService implements AiExecutionApi {

    private final Map<ProviderType, AiProvider> providers = new EnumMap<>(ProviderType.class);
    private final AiCoreProperties properties;
    private final AiRequestValidator validator;
    private final AiProviderRequestMapper requestMapper;

    public DefaultAiExecutionService(List<AiProvider> aiProviders,
                                     AiCoreProperties properties,
                                     AiRequestValidator validator,
                                     AiProviderRequestMapper requestMapper) {
        for (AiProvider provider : aiProviders) {
            this.providers.put(provider.providerType(), provider);
        }
        this.properties = properties;
        this.validator = validator;
        this.requestMapper = requestMapper;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        validator.validateChat(request);
        return resolveProvider(request.getProvider()).chat(requestMapper.mapChat(request, properties));
    }

    @Override
    public void chatStream(ChatRequest request, ChatStreamObserver observer) {
        validator.validateChat(request);
        if (observer == null) {
            throw new IllegalArgumentException("chatStream observer must not be null");
        }
        resolveProvider(request.getProvider()).chatStream(requestMapper.mapChat(request, properties), observer);
    }

    @Override
    public EmbedResponse embed(EmbedRequest request) {
        validator.validateEmbed(request);
        return resolveProvider(request.getProvider()).embed(requestMapper.mapEmbed(request, properties));
    }

    @Override
    public RerankResponse rerank(RerankRequest request) {
        validator.validateRerank(request);
        return resolveProvider(request.getProvider()).rerank(requestMapper.mapRerank(request, properties));
    }

    @Override
    public KbUpsertResponse kbUpsert(KbUpsertRequest request) {
        validator.validateKbUpsert(request);
        return resolveProvider(null).kbUpsert(requestMapper.mapKbUpsert(request));
    }

    @Override
    public KbDeleteResponse kbDelete(KbDeleteRequest request) {
        validator.validateKbDelete(request);
        return resolveProvider(null).kbDelete(requestMapper.mapKbDelete(request));
    }

    @Override
    public KbSearchResponse kbSearch(KbSearchRequest request) {
        validator.validateKbSearch(request);
        return resolveProvider(null).kbSearch(requestMapper.mapKbSearch(request));
    }

    private AiProvider resolveProvider(ProviderType requestedProvider) {
        ProviderType providerType = requestedProvider;
        if (providerType == null) {
            if (properties.isStrictProvider()) {
                throw new IllegalArgumentException("provider is required when ai.core.strict-provider=true");
            }
            providerType = properties.getDefaultProvider();
        }

        AiProvider provider = providers.get(providerType);
        if (provider == null) {
            throw new IllegalStateException("AI provider not found or not enabled: " + providerType);
        }
        return provider;
    }
}
