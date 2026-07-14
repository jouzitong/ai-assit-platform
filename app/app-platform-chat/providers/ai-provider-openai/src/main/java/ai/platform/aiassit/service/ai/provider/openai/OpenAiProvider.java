package ai.platform.aiassit.service.ai.provider.openai;

import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.dto.ChatMessage;
import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.service.ai.api.dto.OutputItem;
import ai.platform.aiassit.service.ai.api.dto.Usage;
import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import ai.platform.aiassit.service.ai.api.enums.FinishReason;
import ai.platform.aiassit.service.ai.api.enums.MessageRole;
import ai.platform.aiassit.service.ai.api.enums.OutputType;
import ai.platform.aiassit.service.ai.api.stream.ChatChunk;
import ai.platform.aiassit.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassit.service.ai.spi.AiChatService;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderChatRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderModel;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderModelListRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import org.arthena.framework.common.exception.BizException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** OpenAI 兼容协议客户端；连接信息必须来自当前请求。 */
@Component
public class OpenAiProvider implements AiChatService {

    private static final int DEFAULT_TIMEOUT_MS = 30_000;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public AiChatClientType chatClientType() {
        return AiChatClientType.SPRING_AI;
    }

    @Override
    public List<ProviderModel> listModels(ProviderModelListRequest request) {
        String baseUrl = requireBaseUrl(request == null ? null : request.getBaseUrl());
        String apiKey = requireApiKey(request == null ? null : request.getApiKey());
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(resolveModelsUrl(baseUrl)))
                    .timeout(resolveTimeout(request == null ? null : request.getTimeoutMs()))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED,
                        response.statusCode() + " " + extractErrorMessage(response.body()));
            }
            return toProviderModels(response.body());
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, ex.getMessage());
        }
    }

    @Override
    public ChatResponse chat(ProviderChatRequest request) {
        OpenAiChatModel chatModel = createChatModel(request);
        org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(
                new Prompt(toSpringMessages(request.getMessages()), toChatOptions(request)));
        return toChatResponse(aiResponse);
    }

    @Override
    public void chatStream(ProviderChatRequest request, ChatStreamObserver observer) {
        if (observer == null) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_OBSERVER);
        }
        Flux<org.springframework.ai.chat.model.ChatResponse> stream = createChatModel(request).stream(
                new Prompt(toSpringMessages(request.getMessages()), toChatOptions(request)));
        try {
            stream.doOnNext(item -> emitChunk(observer, item))
                    .doOnError(observer::onError)
                    .doOnComplete(observer::onComplete)
                    .blockLast();
        } catch (RuntimeException ex) {
            observer.onError(ex);
        }
    }

    private OpenAiChatModel createChatModel(ProviderChatRequest request) {
        String baseUrl = requireBaseUrl(request == null ? null : request.getBaseUrl());
        String apiKey = requireApiKey(request == null ? null : request.getApiKey());
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    private OpenAiChatOptions toChatOptions(ProviderChatRequest request) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(requireModel(request == null ? null : request.getModel()));
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

    private List<Message> toSpringMessages(List<ChatMessage> messages) {
        List<Message> converted = new ArrayList<>();
        for (ChatMessage message : messages == null ? Collections.<ChatMessage>emptyList() : messages) {
            if (message == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            MessageRole role = message.getRole() == null ? MessageRole.USER : message.getRole();
            if (role == MessageRole.SYSTEM) {
                converted.add(new SystemMessage(message.getContent()));
            } else if (role == MessageRole.ASSISTANT) {
                converted.add(new AssistantMessage(message.getContent()));
            } else {
                converted.add(new UserMessage(message.getContent()));
            }
        }
        return converted;
    }

    private List<ProviderModel> toProviderModels(String responseBody) throws Exception {
        JsonNode data = objectMapper.readTree(responseBody).path("data");
        if (!data.isArray()) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID,
                    "models response missing data array");
        }
        List<ProviderModel> models = new ArrayList<>();
        for (JsonNode item : data) {
            if (!item.isObject() || !StringUtils.hasText(item.path("id").asText())) {
                continue;
            }
            ProviderModel model = new ProviderModel();
            model.setId(item.path("id").asText());
            model.setObject(item.path("object").asText(null));
            model.setCreated(item.hasNonNull("created") ? item.get("created").asLong() : null);
            model.setOwnedBy(item.path("owned_by").asText(null));
            models.add(model);
        }
        return models;
    }

    private ChatResponse toChatResponse(org.springframework.ai.chat.model.ChatResponse aiResponse) {
        ChatResponse response = new ChatResponse();
        if (aiResponse.getMetadata() != null) {
            response.setRequestId(aiResponse.getMetadata().getId());
            response.setModel(aiResponse.getMetadata().getModel());
            response.getProviderMeta().put("id", aiResponse.getMetadata().getId());
            response.getProviderMeta().put("model", aiResponse.getMetadata().getModel());
            response.setUsage(toUsage(aiResponse.getMetadata().getUsage()));
        }
        Generation generation = aiResponse.getResult();
        if (generation != null && generation.getOutput() != null
                && StringUtils.hasText(generation.getOutput().getText())) {
            OutputItem item = new OutputItem();
            item.setType(OutputType.TEXT);
            item.setText(generation.getOutput().getText());
            response.getOutputs().add(item);
            response.setFinishReason(toFinishReason(
                    generation.getMetadata() == null ? null : generation.getMetadata().getFinishReason()));
        }
        if (response.getFinishReason() == null) {
            response.setFinishReason(FinishReason.STOP);
        }
        return response;
    }

    private void emitChunk(ChatStreamObserver observer,
                           org.springframework.ai.chat.model.ChatResponse response) {
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null
                || !StringUtils.hasText(generation.getOutput().getText())) {
            return;
        }
        ChatChunk chunk = new ChatChunk();
        chunk.setRequestId(response.getMetadata() == null ? null : response.getMetadata().getId());
        chunk.setOutputType(OutputType.TEXT);
        chunk.setDelta(generation.getOutput().getText());
        observer.onChunk(chunk);
    }

    private Usage toUsage(org.springframework.ai.chat.metadata.Usage usage) {
        Usage result = new Usage();
        if (usage != null) {
            result.setInputTokens(nullSafeInt(usage.getPromptTokens()));
            result.setOutputTokens(nullSafeInt(usage.getCompletionTokens()));
            result.setTotalTokens(nullSafeInt(usage.getTotalTokens()));
        }
        return result;
    }

    private FinishReason toFinishReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return FinishReason.STOP;
        }
        try {
            return FinishReason.valueOf(reason.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return FinishReason.STOP;
        }
    }

    private String resolveModelsUrl(String baseUrl) {
        String normalized = baseUrl;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/models")) {
            return normalized;
        }
        return normalized.endsWith("/v1") ? normalized + "/models" : normalized + "/v1/models";
    }

    private String extractErrorMessage(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("error").path("message");
            if (!message.isTextual()) {
                message = root.path("message");
            }
            return message.isTextual() ? message.asText() : responseBody;
        } catch (Exception ignored) {
            return responseBody;
        }
    }

    private Duration resolveTimeout(Integer timeoutMs) {
        return Duration.ofMillis(timeoutMs != null && timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS);
    }

    private String requireBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_BASE_URL);
        }
        return value.trim();
    }

    private String requireApiKey(String value) {
        if (!StringUtils.hasText(value)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_API_KEY);
        }
        return value.trim();
    }

    private String requireModel(String value) {
        if (!StringUtils.hasText(value)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_API_MODEL);
        }
        return value.trim();
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
