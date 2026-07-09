package ai.platform.aiassit.execution.service.impl;

import ai.platform.aiassit.execution.dto.AiModelTestChatMessageDTO;
import ai.platform.aiassit.execution.dto.AiModelTestChatRequestDTO;
import ai.platform.aiassit.execution.dto.AiModelTestChatResultVO;
import ai.platform.aiassit.execution.service.AiModelTestService;
import ai.platform.aiassit.model.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.model.service.AiModelConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OpenAiCompatibleModelTestService implements AiModelTestService {

    private static final Set<String> SUPPORTED_TEST_MESSAGE_ROLES = Set.of("system", "user", "assistant");
    private static final Duration TEST_CHAT_TIMEOUT = Duration.ofSeconds(30);

    private final AiModelConfigService aiModelConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleModelTestService(AiModelConfigService aiModelConfigService,
                                            ObjectMapper objectMapper) {
        this.aiModelConfigService = aiModelConfigService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public AiModelTestChatResultVO testChat(AiModelTestChatRequestDTO request) {
        long start = System.currentTimeMillis();
        AiModelTestChatResultVO result = new AiModelTestChatResultVO();
        try {
            AiModelConfigDTO current = request != null && request.getId() != null
                    ? aiModelConfigService.get(request.getId())
                    : null;
            String providerCode = trimToNull(request == null ? null : request.getProviderCode());
            String baseUrl = chooseValue(trimToNull(request == null ? null : request.getBaseUrl()),
                    current == null ? null : current.getBaseUrl(), false);
            String apiModel = chooseValue(trimToNull(request == null ? null : request.getApiModel()),
                    current == null ? null : current.getApiModel(), false);
            String apiKey = chooseValue(trimToNull(request == null ? null : request.getApiKey()),
                    current == null ? null : current.getApiKey(), false);

            result.setProviderCode(providerCode);
            result.setApiModel(apiModel);

            validateTestChatPayload(baseUrl, apiModel, apiKey, request == null ? null : request.getMessages());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", apiModel);
            payload.put("messages", buildOpenAiCompatibleMessages(request.getMessages()));
            payload.put("temperature", 0.2);
            payload.put("max_tokens", 512);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(resolveChatCompletionsUrl(baseUrl)))
                    .timeout(resolveTestTimeout(request == null ? null : request.getExtJson()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("模型调用失败：" + response.statusCode() + " " + extractErrorMessage(response.body()));
            }

            result.setAnswer(extractAnswer(response.body()));
            result.setSuccess(Boolean.TRUE);
            return result;
        } catch (Exception ex) {
            result.setSuccess(Boolean.FALSE);
            result.setErrorMessage(ex.getMessage());
            return result;
        } finally {
            result.setDurationMs(System.currentTimeMillis() - start);
        }
    }

    private void validateTestChatPayload(String baseUrl,
                                         String apiModel,
                                         String apiKey,
                                         List<AiModelTestChatMessageDTO> messages) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("Base URL 不能为空");
        }
        if (!StringUtils.hasText(apiModel)) {
            throw new IllegalArgumentException("Provider 模型标识不能为空");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        if (messages == null || messages.stream().noneMatch(message -> StringUtils.hasText(message.getContent()))) {
            throw new IllegalArgumentException("测试消息不能为空");
        }
    }

    private List<Map<String, String>> buildOpenAiCompatibleMessages(List<AiModelTestChatMessageDTO> messages) {
        List<Map<String, String>> result = new ArrayList<>();
        for (AiModelTestChatMessageDTO message : messages) {
            if (message == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            String role = trimToNull(message.getRole());
            role = role == null ? "user" : role.toLowerCase();
            if (!SUPPORTED_TEST_MESSAGE_ROLES.contains(role)) {
                role = "user";
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("role", role);
            item.put("content", message.getContent().trim());
            result.add(item);
        }
        return result;
    }

    private String resolveChatCompletionsUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
    }

    private Duration resolveTestTimeout(Map<String, Object> extJson) {
        Object timeoutValue = extJson == null ? null : extJson.get("testTimeoutMs");
        if (timeoutValue instanceof Number number) {
            long timeoutMs = number.longValue();
            if (timeoutMs >= 1000 && timeoutMs <= 120000) {
                return Duration.ofMillis(timeoutMs);
            }
        }
        return TEST_CHAT_TIMEOUT;
    }

    private String extractAnswer(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isTextual() && StringUtils.hasText(content.asText())) {
            return content.asText();
        }
        JsonNode text = root.path("choices").path(0).path("text");
        if (text.isTextual() && StringUtils.hasText(text.asText())) {
            return text.asText();
        }
        return responseBody;
    }

    private String extractErrorMessage(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("error").path("message");
            if (message.isTextual() && StringUtils.hasText(message.asText())) {
                return message.asText();
            }
            JsonNode msg = root.path("message");
            if (msg.isTextual() && StringUtils.hasText(msg.asText())) {
                return msg.asText();
            }
        } catch (Exception ignored) {
            return responseBody;
        }
        return responseBody;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private <T> T chooseValue(T incoming, T current, boolean replaceNulls) {
        if (replaceNulls) {
            return incoming;
        }
        return incoming != null ? incoming : current;
    }
}
