package ai.platform.aiassist.service.ai.core;

import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.RerankRequest;
import ai.platform.aiassist.service.ai.core.provider.dto.ProviderChatRequest;
import ai.platform.aiassist.service.ai.core.provider.dto.ProviderEmbedRequest;
import ai.platform.aiassist.service.ai.core.provider.dto.ProviderKbDeleteRequest;
import ai.platform.aiassist.service.ai.core.provider.dto.ProviderKbSearchRequest;
import ai.platform.aiassist.service.ai.core.provider.dto.ProviderKbUpsertRequest;
import ai.platform.aiassist.service.ai.core.provider.dto.ProviderRerankRequest;
import org.springframework.stereotype.Component;

@Component
public class AiProviderRequestMapper {

    public ProviderChatRequest mapChat(ChatRequest request, AiCoreProperties properties) {
        ProviderChatRequest target = new ProviderChatRequest();
        target.setModel(resolveModel(request == null ? null : request.getModel(), properties.getDefaultChatModel()));
        target.setMessages(request.getMessages());
        target.setTools(request.getTools());
        target.setResponseFormat(request.getResponseFormat());
        if (request.getOptions() != null) {
            target.setTemperature(request.getOptions().getTemperature());
            target.setTopP(request.getOptions().getTopP());
            target.setMaxTokens(request.getOptions().getMaxTokens());
            target.setTimeoutMs(request.getOptions().getTimeoutMs());
        }
        target.setMeta(request.getMeta());
        target.setExt(request.getExt());
        return target;
    }

    public ProviderEmbedRequest mapEmbed(EmbedRequest request, AiCoreProperties properties) {
        ProviderEmbedRequest target = new ProviderEmbedRequest();
        target.setModel(resolveModel(request.getModel(), properties.getDefaultEmbeddingModel()));
        target.setInputs(request.getInputs());
        target.setMeta(request.getMeta());
        return target;
    }

    public ProviderRerankRequest mapRerank(RerankRequest request, AiCoreProperties properties) {
        ProviderRerankRequest target = new ProviderRerankRequest();
        target.setModel(resolveModel(request.getModel(), properties.getDefaultRerankModel()));
        target.setQuery(request.getQuery());
        target.setCandidates(request.getCandidates());
        target.setTopN(request.getTopN());
        target.setMeta(request.getMeta());
        return target;
    }

    public ProviderKbUpsertRequest mapKbUpsert(KbUpsertRequest request) {
        ProviderKbUpsertRequest target = new ProviderKbUpsertRequest();
        target.setKbId(request.getKbId());
        target.setDocuments(request.getDocuments());
        target.setMeta(request.getMeta());
        return target;
    }

    public ProviderKbDeleteRequest mapKbDelete(KbDeleteRequest request) {
        ProviderKbDeleteRequest target = new ProviderKbDeleteRequest();
        target.setKbId(request.getKbId());
        target.setDocumentIds(request.getDocumentIds());
        target.setMeta(request.getMeta());
        return target;
    }

    public ProviderKbSearchRequest mapKbSearch(KbSearchRequest request) {
        ProviderKbSearchRequest target = new ProviderKbSearchRequest();
        target.setKbId(request.getKbId());
        target.setQuery(request.getQuery());
        target.setTopK(request.getTopK());
        target.setMeta(request.getMeta());
        return target;
    }

    private String resolveModel(String requestedModel, String defaultModel) {
        if (requestedModel == null || requestedModel.isBlank()) {
            return defaultModel;
        }
        return requestedModel;
    }
}
