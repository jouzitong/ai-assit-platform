package ai.platform.aiassit.service.ai.agent.service;

import ai.platform.aiassit.service.ai.agent.config.AiAgentProperties;
import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.service.ai.api.dto.OutputItem;
import ai.platform.aiassit.service.ai.api.dto.Usage;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.enums.FinishReason;
import ai.platform.aiassit.service.ai.api.enums.OutputType;
import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import ai.platform.aiassit.service.ai.api.stream.ChatChunk;
import ai.platform.aiassit.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassit.service.ai.spi.AiChatService;
import ai.platform.aiassit.service.ai.spi.agent.AgentCancellation;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunCommand;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunEvent;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunObserver;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunResult;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntime;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeCapabilities;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeType;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderChatRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderModel;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderModelListRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.provider.ai-agent", name = "enabled", havingValue = "true")
public class AiAgentProvider implements AiChatService, AgentRuntime {

    private static final String PYTHON_SDK_VERSION = "0.18.2";

    private final AiAgentProperties properties;
    private final AiAgentProcessExecutor processExecutor;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiAgentProvider(AiAgentProperties properties,
                           AiAgentProcessExecutor processExecutor,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.processExecutor = processExecutor;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public AiChatClientType chatClientType() {
        return AiChatClientType.AI_AGENT;
    }

    @Override
    public List<ProviderModel> listModels(ProviderModelListRequest request) {
        String baseUrl = resolveValue(request == null ? null : request.getBaseUrl(), properties.getBaseUrl());
        String apiKey = resolveValue(request == null ? null : request.getApiKey(), properties.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_API_KEY);
        }
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(resolveModelsUrl(baseUrl)))
                    .timeout(resolveTimeout(request == null ? null : request.getTimeoutMs()))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
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
        log.debug("ai agent chat request, model={}, messageCount={}",
                request == null ? null : request.getModel(),
                request == null || request.getMessages() == null ? 0 : request.getMessages().size());
        JsonNode responseNode = processExecutor.execute(properties, request);
        return toChatResponse(responseNode);
    }

    @Override
    public AgentRuntimeCapabilities capabilities() {
        return AgentRuntimeCapabilities.builder()
                .runtimeType(AgentRuntimeType.OPENAI_AGENTS_PYTHON)
                .sdkVersion(PYTHON_SDK_VERSION)
                .features(Set.of(
                        "protocol-v2",
                        "function-tools",
                        "agent-as-tool",
                        "handoffs",
                        "skill-on-demand",
                        "platform-events",
                        "actual-usage",
                        "cancellation"
                ))
                .build();
    }

    @Override
    public AgentRunResult run(AgentDefinitionSnapshot snapshot,
                              AgentRunCommand command,
                              AgentRunObserver observer,
                              AgentCancellation cancellation) {
        AgentRunObserver targetObserver = observer == null ? AgentRunObserver.NOOP : observer;
        AgentCancellation targetCancellation = cancellation == null ? AgentCancellation.NONE : cancellation;
        AtomicBoolean failureEventReceived = new AtomicBoolean(false);
        try {
            JsonNode result = processExecutor.executeAgent(
                    properties,
                    snapshot,
                    command,
                    frame -> {
                        AgentRunEvent event = toAgentRunEvent(frame);
                        if ("round.failed".equals(event.getEventType())
                                || "round.cancelled".equals(event.getEventType())) {
                            failureEventReceived.set(true);
                        }
                        targetObserver.onEvent(event);
                    },
                    targetCancellation
            );
            return toAgentRunResult(result, command);
        } catch (RuntimeException ex) {
            if (failureEventReceived.compareAndSet(false, true)) {
                try {
                    targetObserver.onEvent(fallbackAgentFailure(command, targetCancellation, ex));
                } catch (RuntimeException callbackError) {
                    log.warn("ai agent runtime failure event callback failed", callbackError);
                }
            }
            throw ex;
        }
    }

    @Override
    public void chatStream(ProviderChatRequest request, ChatStreamObserver observer) {
        if (observer == null) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_OBSERVER);
        }
        AtomicBoolean receivedFailureActivity = new AtomicBoolean(false);
        try {
            AtomicBoolean receivedDelta = new AtomicBoolean(false);
            JsonNode responseNode = processExecutor.executeStream(properties, request,
                    frame -> {
                        ChatChunk chunk = toStreamChunk(frame);
                        if (StringUtils.hasText(chunk.getDelta())) {
                            receivedDelta.set(true);
                        }
                        if ("ACTIVITY".equalsIgnoreCase(chunk.getProgressType())
                                && "FAILED".equalsIgnoreCase(chunk.getStatus())) {
                            receivedFailureActivity.set(true);
                        }
                        observer.onChunk(chunk);
                    });
            ChatResponse response = toChatResponse(responseNode);
            String text = extractText(response);
            if (!receivedDelta.get() && StringUtils.hasText(text)) {
                ChatChunk chunk = new ChatChunk();
                chunk.setRequestId(response.getRequestId());
                chunk.setOutputType(OutputType.TEXT);
                chunk.setDelta(text);
                chunk.setEventType("answer_delta");
                observer.onChunk(chunk);
            }
            observer.onComplete();
        } catch (Exception ex) {
            if (receivedFailureActivity.compareAndSet(false, true)) {
                try {
                    observer.onChunk(failureActivity(ex));
                } catch (RuntimeException callbackError) {
                    log.warn("ai agent failure activity callback failed", callbackError);
                }
            }
            observer.onError(ex);
        }
    }

    private ChatChunk failureActivity(Throwable error) {
        ChatChunk chunk = new ChatChunk();
        chunk.setEventType("progress");
        chunk.setProgressType("ACTIVITY");
        chunk.setSource("AI_AGENT");
        chunk.setPhase("FAILED");
        chunk.setStatus("FAILED");
        chunk.setMessage(failureMessage(error));
        chunk.getExt().put("activityCode", "ai-agent-execution");
        chunk.getExt().put("activityType", "AI_AGENT_EXECUTION");
        chunk.getExt().put("activityName", "AI Agent 执行");
        chunk.getExt().put("outputSummary", chunk.getMessage());
        return chunk;
    }

    private String failureMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (StringUtils.hasText(message)) {
            String normalized = message.toLowerCase();
            if (normalized.contains("timeout")) {
                return "AI Agent 执行超时";
            }
            if (normalized.contains("interrupt")) {
                return "AI Agent 执行已中断";
            }
            if (normalized.contains("cancel")) {
                return "AI Agent 执行已取消";
            }
            if (normalized.contains("empty output")) {
                return "AI Agent 未返回有效结果";
            }
        }
        return "AI Agent 执行失败";
    }

    private ChatChunk toStreamChunk(JsonNode frame) {
        ChatChunk chunk = new ChatChunk();
        chunk.setRequestId(text(frame, "requestId"));
        String type = text(frame, "type");
        String platformEventType = text(frame, "eventType");
        if (("event".equalsIgnoreCase(type) || "error".equalsIgnoreCase(type))
                && StringUtils.hasText(platformEventType)) {
            if ("assistant.message.delta".equalsIgnoreCase(platformEventType)) {
                chunk.setEventType("answer_delta");
                chunk.setOutputType(OutputType.TEXT);
                chunk.setDelta(text(frame, "delta"));
                chunk.getExt().put("platformEventType", platformEventType);
                return chunk;
            }
            String status = StringUtils.hasText(text(frame, "status"))
                    ? text(frame, "status")
                    : "error".equalsIgnoreCase(type) ? "FAILED" : null;
            chunk.setEventType("progress");
            chunk.setProgressType("ACTIVITY");
            chunk.setSource(StringUtils.hasText(text(frame, "source"))
                    ? text(frame, "source")
                    : "AI_AGENT");
            chunk.setPhase(resolvePhase(status));
            chunk.setStatus(status);
            chunk.setMessage(StringUtils.hasText(text(frame, "message"))
                    ? text(frame, "message")
                    : platformEventType);
            copyObject(frame.get("ext"), chunk.getExt());
            chunk.getExt().put("platformEventType", platformEventType);
            copyIfPresent(frame, chunk.getExt(), "agentCode", "agentVersion", "agentName", "traceId",
                    "sessionCode", "roundCode", "timestamp");
            chunk.getExt().putIfAbsent("activityType", activityType(platformEventType));
            return chunk;
        }
        if ("activity".equalsIgnoreCase(type) || "error".equalsIgnoreCase(type)) {
            chunk.setEventType("progress");
            chunk.setProgressType("ACTIVITY");
            chunk.setSource(text(frame, "source"));
            chunk.setPhase(StringUtils.hasText(text(frame, "phase"))
                    ? text(frame, "phase")
                    : "error".equalsIgnoreCase(type) ? "FAILED" : null);
            chunk.setStatus(StringUtils.hasText(text(frame, "status"))
                    ? text(frame, "status")
                    : "error".equalsIgnoreCase(type) ? "FAILED" : null);
            chunk.setMessage(text(frame, "message"));
            JsonNode extNode = frame.get("ext");
            if (extNode != null && extNode.isObject()) {
                extNode.fields().forEachRemaining(entry ->
                        chunk.getExt().put(entry.getKey(), simpleValue(entry.getValue())));
            }
            chunk.getExt().putIfAbsent("activityType", "error".equalsIgnoreCase(type)
                    ? "AI_AGENT_EXECUTION"
                    : "AI_AGENT_ACTIVITY");
        } else {
            chunk.setEventType("answer_delta");
            chunk.setOutputType(OutputType.TEXT);
            chunk.setDelta(text(frame, "delta"));
        }
        return chunk;
    }

    AgentRunEvent toAgentRunEvent(JsonNode frame) {
        AgentRunEvent event = new AgentRunEvent();
        String type = text(frame, "type");
        String eventType = text(frame, "eventType");
        if (!StringUtils.hasText(eventType)) {
            if ("delta".equalsIgnoreCase(type)) {
                eventType = "assistant.message.delta";
            } else if ("error".equalsIgnoreCase(type)) {
                eventType = "round.failed";
            } else {
                eventType = "agent.activity";
            }
        }
        event.setEventType(eventType);
        event.setRunId(text(frame, "runId"));
        event.setRequestId(text(frame, "requestId"));
        event.setTraceId(text(frame, "traceId"));
        event.setSessionCode(text(frame, "sessionCode"));
        event.setRoundCode(text(frame, "roundCode"));
        event.setAgentCode(text(frame, "agentCode"));
        event.setAgentVersion(nullableInt(frame.get("agentVersion")));
        event.setAgentName(text(frame, "agentName"));
        event.setStatus(StringUtils.hasText(text(frame, "status"))
                ? text(frame, "status")
                : "error".equalsIgnoreCase(type) ? "FAILED" : null);
        event.setDelta(text(frame, "delta"));
        event.setMessage(text(frame, "message"));
        event.setTimestamp(parseInstant(text(frame, "timestamp")));
        copyObject(frame.get("ext"), event.getExt());
        event.getExt().put("platformEventType", eventType);
        copyIfPresent(frame, event.getExt(), "source", "activityCode", "activityType", "toolCode", "callId",
                "artifactCode", "artifactType", "summary");
        return event;
    }

    private AgentRunResult toAgentRunResult(JsonNode node, AgentRunCommand command) {
        AgentRunResult result = new AgentRunResult();
        result.setRunId(StringUtils.hasText(text(node, "runId"))
                ? text(node, "runId")
                : command == null ? null : command.getRunId());
        result.setFinalOutput(StringUtils.hasText(text(node, "finalOutput"))
                ? text(node, "finalOutput")
                : firstOutputText(node));
        result.setFinalAgentCode(text(node, "finalAgentCode"));
        result.setStatus(StringUtils.hasText(text(node, "status")) ? text(node, "status") : "SUCCESS");
        JsonNode usageNode = node == null ? null : node.get("usage");
        if (usageNode != null && usageNode.isObject()) {
            Usage usage = new Usage();
            usage.setInputTokens(intValue(usageNode.get("inputTokens")));
            usage.setOutputTokens(intValue(usageNode.get("outputTokens")));
            usage.setTotalTokens(intValue(usageNode.get("totalTokens")));
            result.setUsage(usage);
        }
        JsonNode artifacts = node == null ? null : node.get("artifacts");
        if (artifacts != null && artifacts.isArray()) {
            for (JsonNode artifact : artifacts) {
                if (artifact.isObject()) {
                    result.getArtifacts().add(objectMapper.convertValue(
                            artifact,
                            new TypeReference<Map<String, Object>>() { }
                    ));
                }
            }
        }
        copyObject(node == null ? null : node.get("providerMeta"), result.getProviderMeta());
        return result;
    }

    private AgentRunEvent fallbackAgentFailure(AgentRunCommand command,
                                               AgentCancellation cancellation,
                                               Throwable error) {
        AgentRunEvent event = new AgentRunEvent();
        boolean cancelled = cancellation != null && cancellation.isCancellationRequested();
        event.setEventType(cancelled ? "round.cancelled" : "round.failed");
        event.setRunId(command == null ? null : command.getRunId());
        event.setRequestId(command == null ? null : command.getRequestId());
        event.setTraceId(command == null ? null : command.getTraceId());
        event.setSessionCode(command == null ? null : command.getSessionCode());
        event.setRoundCode(command == null ? null : command.getRoundCode());
        event.setStatus(cancelled ? "CANCELLED" : "FAILED");
        event.setMessage(failureMessage(error));
        event.getExt().put("source", "OPENAI_AGENTS_PYTHON");
        return event;
    }

    private String firstOutputText(JsonNode node) {
        JsonNode outputs = node == null ? null : node.get("outputs");
        if (outputs == null || !outputs.isArray()) {
            return null;
        }
        for (JsonNode output : outputs) {
            if (StringUtils.hasText(text(output, "text"))) {
                return text(output, "text");
            }
        }
        return null;
    }

    private String resolvePhase(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return switch (status.toUpperCase()) {
            case "SUCCESS", "COMPLETED" -> "COMPLETED";
            case "FAILED", "CANCELLED" -> status.toUpperCase();
            default -> "RUNNING";
        };
    }

    private String activityType(String eventType) {
        if (eventType.startsWith("tool.")) {
            return "TOOL_CALL";
        }
        if (eventType.startsWith("handoff.")) {
            return "AGENT_HANDOFF";
        }
        if (eventType.startsWith("skill.")) {
            return "SKILL_LOAD";
        }
        return "AI_AGENT_EXECUTION";
    }

    private void copyObject(JsonNode node, Map<String, Object> target) {
        if (node == null || !node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> target.put(entry.getKey(), simpleValue(entry.getValue())));
    }

    private void copyIfPresent(JsonNode source, Map<String, Object> target, String... fields) {
        if (source == null) {
            return;
        }
        for (String field : fields) {
            JsonNode value = source.get(field);
            if (value != null && !value.isNull()) {
                target.put(field, simpleValue(value));
            }
        }
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return Instant.now();
        }
    }

    private Integer nullableInt(JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }

    private ChatResponse toChatResponse(JsonNode node) {
        ChatResponse response = new ChatResponse();
        response.setRequestId(text(node, "requestId"));
        response.setModel(text(node, "model"));
        response.setFinishReason(resolveFinishReason(text(node, "finishReason")));

        JsonNode outputsNode = node.get("outputs");
        if (outputsNode != null && outputsNode.isArray()) {
            for (JsonNode itemNode : outputsNode) {
                OutputItem item = new OutputItem();
                String type = text(itemNode, "type");
                item.setType(resolveOutputType(type));
                item.setText(text(itemNode, "text"));
                JsonNode jsonNode = itemNode.get("json");
                if (jsonNode != null && jsonNode.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        item.getJson().put(entry.getKey(), simpleValue(entry.getValue()));
                    }
                }
                response.getOutputs().add(item);
            }
        }

        JsonNode usageNode = node.get("usage");
        if (usageNode != null && usageNode.isObject()) {
            Usage usage = new Usage();
            usage.setInputTokens(intValue(usageNode.get("inputTokens")));
            usage.setOutputTokens(intValue(usageNode.get("outputTokens")));
            usage.setTotalTokens(intValue(usageNode.get("totalTokens")));
            response.setUsage(usage);
        }

        JsonNode providerMetaNode = node.get("providerMeta");
        if (providerMetaNode != null && providerMetaNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = providerMetaNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                response.getProviderMeta().put(entry.getKey(), simpleValue(entry.getValue()));
            }
        }

        if (response.getFinishReason() == null) {
            response.setFinishReason(FinishReason.STOP);
        }
        return response;
    }

    private String extractText(ChatResponse response) {
        return response.getOutputs().stream()
                .filter(item -> item.getType() == OutputType.TEXT && StringUtils.hasText(item.getText()))
                .map(OutputItem::getText)
                .findFirst()
                .orElse(null);
    }

    private OutputType resolveOutputType(String type) {
        if (!StringUtils.hasText(type)) {
            return OutputType.TEXT;
        }
        try {
            return OutputType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return OutputType.TEXT;
        }
    }

    private FinishReason resolveFinishReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return FinishReason.STOP;
        }
        try {
            return FinishReason.valueOf(reason.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return FinishReason.STOP;
        }
    }

    private Object simpleValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isFloat() || node.isDouble() || node.isBigDecimal()) {
            return node.asDouble();
        }
        if (node.isObject() || node.isArray()) {
            return objectMapper.convertValue(node, Object.class);
        }
        return node.asText();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private int intValue(JsonNode node) {
        return node == null || node.isNull() ? 0 : node.asInt();
    }

    private List<ProviderModel> toProviderModels(String responseBody) throws Exception {
        JsonNode data = objectMapper.readTree(responseBody).path("data");
        if (!data.isArray()) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_RESPONSE_INVALID, "models response missing data array");
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

    private String resolveModelsUrl(String baseUrl) {
        String normalized = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "https://api.openai.com/v1";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/models")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/models";
        }
        return normalized + "/v1/models";
    }

    private Duration resolveTimeout(Integer requestTimeoutMs) {
        Integer timeoutMs = requestTimeoutMs != null && requestTimeoutMs > 0
                ? requestTimeoutMs
                : properties.getTimeoutMs();
        return Duration.ofMillis(timeoutMs != null && timeoutMs > 0 ? timeoutMs : 60000L);
    }

    private String resolveValue(String requestedValue, String defaultValue) {
        return StringUtils.hasText(requestedValue) ? requestedValue.trim() : defaultValue;
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
}
