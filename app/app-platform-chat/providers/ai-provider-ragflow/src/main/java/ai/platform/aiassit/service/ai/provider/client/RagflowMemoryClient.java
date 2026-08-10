package ai.platform.aiassit.service.ai.provider.client;

import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.memory.enums.MemoryType;
import ai.platform.aiassit.service.ai.provider.config.RagflowProperties;
import ai.platform.aiassit.service.ai.provider.dto.RagflowMemoryResponseMapper;
import ai.platform.aiassit.service.ai.spi.memory.MemoryProviderException;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryDescriptor;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryMessage;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryPageResponse;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryRecentResponse;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemorySearchResponse;
import ai.platform.aiassit.service.ai.spi.memory.dto.MemoryWriteResponse;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryCreateRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryDeleteRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryForgetRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryGetRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryListRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryRecentRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemorySearchRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryStatusRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryUpdateRequest;
import ai.platform.aiassit.service.ai.spi.memory.dto.ProviderMemoryWriteRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** RAGFlow v0.24+ Memory HTTP client. Request and response bodies are never logged. */
@Component
public class RagflowMemoryClient {

    private static final String ERROR_INVALID_REQUEST = "MEMORY_PROVIDER_REQUEST_INVALID";
    private static final String ERROR_HTTP = "MEMORY_PROVIDER_HTTP_ERROR";
    private static final String ERROR_IO = "MEMORY_PROVIDER_IO_ERROR";
    private static final String ERROR_RESPONSE = "MEMORY_PROVIDER_RESPONSE_INVALID";

    private final RagflowProperties properties;
    private final ObjectMapper objectMapper;
    private final RagflowMemoryResponseMapper responseMapper;
    private final HttpClient httpClient;

    public RagflowMemoryClient(RagflowProperties properties,
                               ObjectMapper objectMapper,
                               RagflowMemoryResponseMapper responseMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.responseMapper = responseMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(defaultTimeoutMs()))
                .build();
    }

    public MemoryDescriptor create(ProviderMemoryCreateRequest request) {
        require(request != null, "create request");
        requireText(request.getName(), "name");
        requireText(request.getEmbeddingModel(), "embeddingModel");
        requireText(request.getExtractionModel(), "extractionModel");
        require(request.getMemoryTypes() != null && !request.getMemoryTypes().isEmpty(), "memoryTypes");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", request.getName().trim());
        body.put("memory_type", request.getMemoryTypes().stream()
                .map(type -> type.name().toLowerCase(Locale.ROOT)).toList());
        body.put("embd_id", request.getEmbeddingModel().trim());
        body.put("llm_id", request.getExtractionModel().trim());
        putText(body, "permission", request.getPermission());
        putPositive(body, "memory_size", request.getMemorySize());
        putText(body, "forgetting_policy", request.getForgettingPolicy());
        JsonNode data = data(call("POST", "/api/v1/memories", body, request.getMeta(), true));
        MemoryDescriptor descriptor = responseMapper.descriptor(data, null);
        requireText(descriptor.getMemoryId(), "RAGFlow memory id");
        return descriptor;
    }

    public MemoryDescriptor get(ProviderMemoryGetRequest request) {
        require(request != null, "get request");
        String memoryId = requireText(request.getMemoryId(), "memoryId");
        JsonNode data = data(call("GET", "/api/v1/memories/" + encodePath(memoryId) + "/config",
                null, request.getMeta(), false));
        return responseMapper.descriptor(data, memoryId);
    }

    public MemoryDescriptor update(ProviderMemoryUpdateRequest request) {
        require(request != null, "update request");
        String memoryId = requireText(request.getMemoryId(), "memoryId");
        Map<String, Object> body = new LinkedHashMap<>();
        putText(body, "name", request.getName());
        putText(body, "llm_id", request.getExtractionModel());
        putText(body, "permission", request.getPermission());
        putPositive(body, "memory_size", request.getMemorySize());
        putText(body, "forgetting_policy", request.getForgettingPolicy());
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        require(!body.isEmpty(), "update body");
        JsonNode data = data(call("PUT", "/api/v1/memories/" + encodePath(memoryId),
                body, request.getMeta(), true));
        return responseMapper.descriptor(data, memoryId);
    }

    public void delete(ProviderMemoryDeleteRequest request) {
        require(request != null, "delete request");
        String memoryId = requireText(request.getMemoryId(), "memoryId");
        // Deleting an already removed resource is a successful idempotent cleanup outcome.
        call("DELETE", "/api/v1/memories/" + encodePath(memoryId), null, request.getMeta(), true, true);
    }

    public MemoryWriteResponse addConversation(ProviderMemoryWriteRequest request) {
        require(request != null, "write request");
        require(request.getMemoryIds() != null && !request.getMemoryIds().isEmpty(), "memoryIds");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("memory_id", request.getMemoryIds().stream().map(value -> requireText(value, "memoryId")).toList());
        body.put("agent_id", requireText(request.getAgentId(), "agentId"));
        body.put("session_id", requireText(request.getSessionId(), "sessionId"));
        putText(body, "external_id", request.getExternalId());
        putText(body, "user_id", request.getUserId());
        body.put("user_input", requireText(request.getUserInput(), "userInput"));
        body.put("agent_response", requireText(request.getAgentResponse(), "agentResponse"));
        JsonNode root = call("POST", "/api/v1/messages", body, request.getMeta(), true);
        JsonNode data = data(root);
        MemoryWriteResponse result = new MemoryWriteResponse();
        result.setAccepted(true);
        result.setProviderMessageId(firstText(data, "message_id", "messageId", "id"));
        result.setProviderStatus(firstText(data, "status", "state"));
        if (!StringUtils.hasText(result.getProviderStatus())) {
            result.setProviderStatus("ACCEPTED");
        }
        return result;
    }

    public MemoryPageResponse list(ProviderMemoryListRequest request) {
        require(request != null, "list request");
        String memoryId = requireText(request.getMemoryId(), "memoryId");
        Map<String, Object> query = new LinkedHashMap<>();
        putText(query, "external_id", request.getExternalId());
        putText(query, "agent_id", request.getAgentId());
        putText(query, "session_id", request.getSessionId());
        putText(query, "user_id", request.getUserId());
        query.put("page", positive(request.getPage(), 1));
        query.put("page_size", positive(request.getPageSize(), 50));
        JsonNode data = data(call("GET", queryPath("/api/v1/memories/" + encodePath(memoryId), query),
                null, request.getMeta(), false));
        MemoryPageResponse result = new MemoryPageResponse();
        result.setItems(hydrateMissingContent(responseMapper.messages(data), request.getMeta()));
        result.setTotal(responseMapper.total(data, result.getItems().size()));
        return result;
    }

    /**
     * RAGFlow's paged Memory endpoint can return message metadata without the message body.
     * Retrieve the body for extracted messages before crossing the provider boundary so callers
     * can apply their own visibility and ownership rules to actual content. Raw parent messages
     * remain metadata-only because they are not exposed as user-facing memory items.
     */
    private List<MemoryMessage> hydrateMissingContent(List<MemoryMessage> messages, RequestMeta meta) {
        for (MemoryMessage message : messages) {
            if (message == null || message.getMemoryType() == MemoryType.RAW
                    || StringUtils.hasText(message.getContent())
                    || !StringUtils.hasText(message.getMemoryId())
                    || !StringUtils.hasText(message.getMessageId())) {
                continue;
            }
            JsonNode contentData = data(call("GET", messageContentPath(
                    message.getMemoryId(), message.getMessageId()), null, meta, false));
            String content = responseMapper.content(contentData);
            if (StringUtils.hasText(content)) {
                message.setContent(content);
            }
        }
        return messages;
    }

    private String messageContentPath(String memoryId, String messageId) {
        return "/api/v1/messages/" + encodePath(requireText(memoryId, "memoryId")) + ":"
                + encodePath(requireText(messageId, "messageId")) + "/content";
    }

    public MemorySearchResponse search(ProviderMemorySearchRequest request) {
        require(request != null, "search request");
        requireText(request.getQuery(), "query");
        require(request.getMemoryIds() != null && !request.getMemoryIds().isEmpty(), "memoryIds");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("query", request.getQuery().trim());
        query.put("memory_id", String.join(",", request.getMemoryIds()));
        putText(query, "agent_id", request.getAgentId());
        putText(query, "session_id", request.getSessionId());
        putText(query, "user_id", request.getUserId());
        if (request.getSimilarityThreshold() != null) {
            query.put("similarity_threshold", ratio(request.getSimilarityThreshold(), 0.2D));
        }
        if (request.getKeywordsSimilarityWeight() != null) {
            query.put("keywords_similarity_weight", ratio(request.getKeywordsSimilarityWeight(), 0.7D));
        }
        query.put("top_n", positive(request.getTopN(), 10));
        JsonNode data = data(call("GET", queryPath("/api/v1/messages/search", query),
                null, request.getMeta(), false));
        MemorySearchResponse result = new MemorySearchResponse();
        result.setItems(responseMapper.messages(data));
        result.setTotal(responseMapper.total(data, result.getItems().size()));
        return result;
    }

    public MemoryRecentResponse recent(ProviderMemoryRecentRequest request) {
        require(request != null, "recent request");
        require(request.getMemoryIds() != null && !request.getMemoryIds().isEmpty(), "memoryIds");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("memory_id", String.join(",", request.getMemoryIds()));
        putText(query, "agent_id", request.getAgentId());
        putText(query, "session_id", request.getSessionId());
        query.put("limit", positive(request.getLimit(), 10));
        JsonNode data = data(call("GET", queryPath("/api/v1/messages", query),
                null, request.getMeta(), false));
        MemoryRecentResponse result = new MemoryRecentResponse();
        result.setItems(responseMapper.messages(data));
        result.setTotal(responseMapper.total(data, result.getItems().size()));
        return result;
    }

    public void updateStatus(ProviderMemoryStatusRequest request) {
        require(request != null, "status request");
        String path = messagePath(request.getMemoryId(), request.getMessageId());
        call("PUT", path, Map.of("status", request.isEnabled()), request.getMeta(), true);
    }

    public void forget(ProviderMemoryForgetRequest request) {
        require(request != null, "forget request");
        call("DELETE", messagePath(request.getMemoryId(), request.getMessageId()),
                null, request.getMeta(), true, true);
    }

    private String messagePath(String memoryId, String messageId) {
        return "/api/v1/messages/" + encodePath(requireText(memoryId, "memoryId")) + ":"
                + encodePath(requireText(messageId, "messageId"));
    }

    private JsonNode call(String method, String path, Object body, RequestMeta meta, boolean write) {
        return call(method, path, body, meta, write, false);
    }

    private JsonNode call(String method, String path, Object body, RequestMeta meta,
                          boolean write, boolean notFoundIsSuccess) {
        String payload;
        try {
            payload = body == null ? null : objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new MemoryProviderException(ERROR_INVALID_REQUEST, "Unable to serialize Memory request", false, ex);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(resolveBaseUrl(meta) + path))
                .timeout(Duration.ofMillis(timeoutMs(meta)))
                .header("Accept", "application/json");
        applyAuthHeader(builder, meta);
        if (payload != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, payload == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(payload));
        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MemoryProviderException(ERROR_IO, "Memory provider request interrupted", write, ex);
        } catch (IOException ex) {
            throw new MemoryProviderException(ERROR_IO, "Memory provider request failed", write, ex);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (notFoundIsSuccess && response.statusCode() == 404) {
                return objectMapper.createObjectNode().put("code", 0);
            }
            boolean uncertain = write && response.statusCode() >= 500;
            throw new MemoryProviderException(ERROR_HTTP,
                    "Memory provider HTTP status " + response.statusCode(), uncertain, null);
        }
        try {
            if (!StringUtils.hasText(response.body())) {
                return objectMapper.createObjectNode().put("code", 0);
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root == null || !successful(root.get("code"))) {
                throw new MemoryProviderException(ERROR_RESPONSE, "Memory provider rejected the request", false, null);
            }
            return root;
        } catch (MemoryProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MemoryProviderException(ERROR_RESPONSE, "Memory provider response is invalid", write, ex);
        }
    }

    private JsonNode data(JsonNode root) {
        return root == null ? objectMapper.nullNode() : root.path("data");
    }

    private boolean successful(JsonNode code) {
        if (code == null || code.isNull()) {
            return true;
        }
        if (code.isNumber()) {
            return code.asLong() == 0L;
        }
        return code.isTextual() && "0".equals(code.asText().trim());
    }

    private String resolveBaseUrl(RequestMeta meta) {
        String value = firstText(metaText(meta, "ragflowBaseUrl"), metaText(meta, "knowledgeClientUrl"),
                properties.getBaseUrl());
        String baseUrl = requireText(value, "baseUrl").replaceAll("/+$", "");
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new MemoryProviderException(ERROR_INVALID_REQUEST, "Memory provider base URL is invalid", false, null);
        }
        return baseUrl;
    }

    private void applyAuthHeader(HttpRequest.Builder builder, RequestMeta meta) {
        Object configured = meta == null || meta.getExt() == null ? null : meta.getExt().get("knowledgeClientAuth");
        if (configured instanceof Map<?, ?> auth) {
            String type = firstText(text(auth.get("type")), "bearer").toLowerCase(Locale.ROOT);
            if ("none".equals(type)) {
                return;
            }
            String value = requireText(text(auth.get("value")), "apiKey");
            String header = firstText(text(auth.get("headerName")), "Authorization");
            String prefix = text(auth.get("prefix"));
            if (!StringUtils.hasText(prefix) && "bearer".equals(type)) {
                prefix = "Bearer ";
            }
            builder.header(header, (prefix == null ? "" : prefix) + value);
            return;
        }
        builder.header("Authorization", "Bearer " + requireText(
                firstText(metaText(meta, "ragflowApiKey"), properties.getApiKey()), "apiKey"));
    }

    private int timeoutMs(RequestMeta meta) {
        Object value = meta == null || meta.getExt() == null ? null : meta.getExt().get("memoryTimeoutMs");
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        try {
            int parsed = value == null ? defaultTimeoutMs() : Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : defaultTimeoutMs();
        } catch (NumberFormatException ignored) {
            return defaultTimeoutMs();
        }
    }

    private int defaultTimeoutMs() {
        return properties.getTimeoutMs() == null || properties.getTimeoutMs() <= 0
                ? 30_000 : properties.getTimeoutMs();
    }

    private String queryPath(String path, Map<String, Object> query) {
        StringBuilder result = new StringBuilder(path);
        boolean first = !path.contains("?");
        for (Map.Entry<String, Object> entry : query.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            result.append(first ? '?' : '&');
            first = false;
            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            result.append('=');
            result.append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private String encodePath(String value) {
        return value.replace("%", "%25").replace("/", "%2F").replace(":", "%3A");
    }

    private String metaText(RequestMeta meta, String key) {
        return meta == null || meta.getExt() == null ? null : text(meta.getExt().get(key));
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isValueNode() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new MemoryProviderException(ERROR_INVALID_REQUEST,
                    "Required Memory provider field is missing: " + field, false, null);
        }
        return value.trim();
    }

    private void require(boolean valid, String field) {
        if (!valid) {
            throw new MemoryProviderException(ERROR_INVALID_REQUEST,
                    "Required Memory provider field is missing: " + field, false, null);
        }
    }

    private void putText(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private void putPositive(Map<String, Object> target, String key, Integer value) {
        if (value != null && value > 0) {
            target.put(key, value);
        }
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private double ratio(Double value, double fallback) {
        return value == null || !Double.isFinite(value) || value < 0D || value > 1D ? fallback : value;
    }
}
