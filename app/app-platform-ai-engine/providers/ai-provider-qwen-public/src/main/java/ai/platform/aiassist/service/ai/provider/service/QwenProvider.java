package ai.platform.aiassist.service.ai.provider.service;

import ai.platform.aiassist.service.ai.api.dto.ChatMessage;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassist.service.ai.api.dto.EmbeddingItem;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.OutputItem;
import ai.platform.aiassist.service.ai.api.dto.RerankResponse;
import ai.platform.aiassist.service.ai.api.enums.FinishReason;
import ai.platform.aiassist.service.ai.api.enums.MessageRole;
import ai.platform.aiassist.service.ai.api.enums.OutputType;
import ai.platform.aiassist.service.ai.api.enums.ProviderType;
import ai.platform.aiassist.service.ai.api.stream.ChatChunk;
import ai.platform.aiassist.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassist.service.ai.provider.client.QwenKnowledgeBaseClient;
import ai.platform.aiassist.service.ai.provider.config.QwenProperties;
import ai.platform.aiassist.service.ai.spi.AiProvider;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderChatRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderEmbedRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderKbDeleteRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderKbSearchRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderKbUpsertRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderRerankRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.provider.qwen", name = "enabled", havingValue = "true")
public class QwenProvider implements AiProvider {

    private final QwenProperties properties;
    private final OpenAiChatModel chatModel;
    private final OpenAiEmbeddingModel embeddingModel;
    private final QwenKnowledgeBaseClient knowledgeBaseClient;

    public QwenProvider(QwenProperties properties,
                        OpenAiChatModel qwenChatModel,
                        OpenAiEmbeddingModel qwenEmbeddingModel,
                        QwenKnowledgeBaseClient knowledgeBaseClient) {
        this.properties = properties;
        this.chatModel = qwenChatModel;
        this.embeddingModel = qwenEmbeddingModel;
        this.knowledgeBaseClient = knowledgeBaseClient;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.DASHSCOPE;
    }

    @Override
    public ChatResponse chat(ProviderChatRequest request) {
        log.debug("chat request: {}", request);
        Prompt prompt = new Prompt(toSpringMessages(request.getMessages()), toChatOptions(request));
        org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(prompt);
        return toChatResponse(aiResponse);
    }

    @Override
    public void chatStream(ProviderChatRequest request, ChatStreamObserver observer) {
        Prompt prompt = new Prompt(toSpringMessages(request.getMessages()), toChatOptions(request));
        Flux<org.springframework.ai.chat.model.ChatResponse> stream = chatModel.stream(prompt);
        try {
            stream.doOnNext(item -> emitChunk(observer, item))
                    .doOnError(observer::onError)
                    .doOnComplete(observer::onComplete)
                    .blockLast();
        } catch (RuntimeException ex) {
            observer.onError(ex);
        }
    }

    @Override
    public EmbedResponse embed(ProviderEmbedRequest request) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(resolveModel(request.getModel(), "text-embedding-v3"))
                .build();
        org.springframework.ai.embedding.EmbeddingResponse aiResponse =
                embeddingModel.call(new EmbeddingRequest(safeList(request.getInputs()), options));
        return toEmbedResponse(aiResponse, options.getModel());
    }

    @Override
    public RerankResponse rerank(ProviderRerankRequest request) {
        throw unsupported("rerank");
    }

    @Override
    public KbUpsertResponse kbUpsert(ProviderKbUpsertRequest request) {
        List<ai.platform.aiassist.service.ai.api.dto.KbDocument> documents = safeList(request.getDocuments());
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("kbUpsert documents must not be empty");
        }
        try {
            String workspaceId = knowledgeBaseClient.resolveWorkspaceId(request.getMeta());
            QwenKnowledgeBaseClient.UpsertResult result =
                    knowledgeBaseClient.upsert(workspaceId, request.getKbId(), documents, request.getMeta());
            KbUpsertResponse response = new KbUpsertResponse();
            response.setKbId(result.kbId());
            response.setAccepted(result.accepted());
            response.setFailed(result.failedDocumentIds().size());
            response.setFailedDocumentIds(result.failedDocumentIds());
            return response;
        } catch (Exception ex) {
            throw new IllegalStateException("Qwen knowledge base upsert failed", ex);
        }
    }

    @Override
    public KbDeleteResponse kbDelete(ProviderKbDeleteRequest request) {
        if (!StringUtils.hasText(request.getKbId())) {
            throw new IllegalArgumentException("kbDelete kbId must not be empty");
        }
        List<String> documentIds = safeList(request.getDocumentIds());
        if (documentIds.isEmpty()) {
            throw new IllegalArgumentException("kbDelete documentIds must not be empty");
        }
        try {
            String workspaceId = knowledgeBaseClient.resolveWorkspaceId(request.getMeta());
            int deleted = knowledgeBaseClient.delete(workspaceId, request.getKbId(), documentIds);
            KbDeleteResponse response = new KbDeleteResponse();
            response.setKbId(request.getKbId());
            response.setDeleted(deleted);
            return response;
        } catch (Exception ex) {
            throw new IllegalStateException("Qwen knowledge base delete failed", ex);
        }
    }

    @Override
    public KbSearchResponse kbSearch(ProviderKbSearchRequest request) {
        if (!StringUtils.hasText(request.getKbId())) {
            throw new IllegalArgumentException("kbSearch kbId must not be empty");
        }
        if (!StringUtils.hasText(request.getQuery())) {
            throw new IllegalArgumentException("kbSearch query must not be empty");
        }
        try {
            String workspaceId = knowledgeBaseClient.resolveWorkspaceId(request.getMeta());
            KbSearchResponse response = new KbSearchResponse();
            response.setKbId(request.getKbId());
            response.setItems(knowledgeBaseClient.search(workspaceId, request.getKbId(), request.getQuery(), request.getTopK()));
            return response;
        } catch (Exception ex) {
            throw new IllegalStateException("Qwen knowledge base search failed", ex);
        }
    }

    private List<Message> toSpringMessages(List<ChatMessage> messages) {
        List<Message> converted = new ArrayList<>();
        for (ChatMessage msg : safeList(messages)) {
            if (msg == null || !StringUtils.hasText(msg.getContent())) {
                continue;
            }
            MessageRole role = msg.getRole() == null ? MessageRole.USER : msg.getRole();
            if (role == MessageRole.SYSTEM) {
                converted.add(new SystemMessage(msg.getContent()));
                continue;
            }
            if (role == MessageRole.ASSISTANT) {
                converted.add(new AssistantMessage(msg.getContent()));
                continue;
            }
            converted.add(new UserMessage(msg.getContent()));
        }
        return converted;
    }

    private OpenAiChatOptions toChatOptions(ProviderChatRequest request) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(resolveModel(request.getModel(), properties.getDefaultModel()));
        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }
        if (request.getTopP() != null) {
            builder.topP(request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            builder.maxTokens(request.getMaxTokens());
        }
        return builder.build();
    }

    private ChatResponse toChatResponse(org.springframework.ai.chat.model.ChatResponse aiResponse) {
        ChatResponse out = new ChatResponse();
        if (aiResponse.getMetadata() != null) {
            out.setRequestId(aiResponse.getMetadata().getId());
            out.setModel(aiResponse.getMetadata().getModel());
            out.getProviderMeta().put("id", aiResponse.getMetadata().getId());
            out.getProviderMeta().put("model", aiResponse.getMetadata().getModel());
            out.setUsage(toUsage(aiResponse.getMetadata().getUsage()));
        }
        Generation result = aiResponse.getResult();
        if (result != null && result.getOutput() != null && StringUtils.hasText(result.getOutput().getText())) {
            OutputItem item = new OutputItem();
            item.setType(OutputType.TEXT);
            item.setText(result.getOutput().getText());
            out.getOutputs().add(item);
            out.setFinishReason(toFinishReason(result.getMetadata() == null ? null : result.getMetadata().getFinishReason()));
        }
        if (out.getFinishReason() == null) {
            out.setFinishReason(FinishReason.STOP);
        }
        return out;
    }

    private EmbedResponse toEmbedResponse(org.springframework.ai.embedding.EmbeddingResponse aiResponse, String model) {
        EmbedResponse out = new EmbedResponse();
        out.setModel(model);
        if (aiResponse.getMetadata() != null) {
            out.setModel(Objects.toString(aiResponse.getMetadata().getModel(), model));
            out.setUsage(toUsage(aiResponse.getMetadata().getUsage()));
        }
        aiResponse.getResults().forEach(embedding -> {
            EmbeddingItem item = new EmbeddingItem();
            item.setIndex(embedding.getIndex());
            float[] vector = embedding.getOutput();
            List<Double> values = new ArrayList<>(vector.length);
            for (float value : vector) {
                values.add((double) value);
            }
            item.setVector(values);
            out.getVectors().add(item);
        });
        return out;
    }

    private void emitChunk(ChatStreamObserver observer, org.springframework.ai.chat.model.ChatResponse item) {
        Generation generation = item.getResult();
        if (generation == null || generation.getOutput() == null || !StringUtils.hasText(generation.getOutput().getText())) {
            return;
        }
        ChatChunk chunk = new ChatChunk();
        chunk.setRequestId(item.getMetadata() == null ? null : item.getMetadata().getId());
        chunk.setOutputType(OutputType.TEXT);
        chunk.setDelta(generation.getOutput().getText());
        observer.onChunk(chunk);
    }

    private ai.platform.aiassist.service.ai.api.dto.Usage toUsage(org.springframework.ai.chat.metadata.Usage usage) {
        ai.platform.aiassist.service.ai.api.dto.Usage out = new ai.platform.aiassist.service.ai.api.dto.Usage();
        if (usage == null) {
            return out;
        }
        out.setInputTokens(nullSafeInt(usage.getPromptTokens()));
        out.setOutputTokens(nullSafeInt(usage.getCompletionTokens()));
        out.setTotalTokens(nullSafeInt(usage.getTotalTokens()));
        return out;
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private FinishReason toFinishReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return FinishReason.STOP;
        }
        String normalized = reason.toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return FinishReason.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return FinishReason.STOP;
        }
    }

    private String resolveModel(String requested, String fallback) {
        if (StringUtils.hasText(requested)) {
            return requested;
        }
        return fallback;
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private UnsupportedOperationException unsupported(String capability) {
        return new UnsupportedOperationException(
                "Qwen provider does not support " + capability + " in current Spring AI implementation");
    }
}
