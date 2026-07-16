package ai.platform.aiassit.service.ai.agent.service;

import ai.platform.aiassit.service.ai.agent.config.AiAgentProperties;
import ai.platform.aiassit.service.ai.api.dto.Usage;
import ai.platform.aiassit.service.ai.spi.agent.AgentCancellation;
import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionSnapshot;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunCommand;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunEvent;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunObserver;
import ai.platform.aiassit.service.ai.spi.agent.AgentRunResult;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntime;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeCapabilities;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Java binding for the bundled OpenAI Agents TypeScript worker. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.provider.ai-agent", name = "enabled", havingValue = "true")
public class AiAgentTypescriptRuntime implements AgentRuntime {

    private static final String TYPESCRIPT_SDK_VERSION = "0.13.4";

    private final AiAgentProperties properties;
    private final AiAgentTypescriptProcessExecutor processExecutor;
    private final ObjectMapper objectMapper;

    public AiAgentTypescriptRuntime(AiAgentProperties properties,
                                    AiAgentTypescriptProcessExecutor processExecutor,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.processExecutor = processExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentRuntimeCapabilities capabilities() {
        return AgentRuntimeCapabilities.builder()
                .runtimeType(AgentRuntimeType.OPENAI_AGENTS_TYPESCRIPT)
                .sdkVersion(TYPESCRIPT_SDK_VERSION)
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
                        AgentRunEvent event = toEvent(frame);
                        if ("round.failed".equals(event.getEventType())
                                || "round.cancelled".equals(event.getEventType())) {
                            failureEventReceived.set(true);
                        }
                        targetObserver.onEvent(event);
                    },
                    targetCancellation
            );
            return toResult(result, command);
        } catch (RuntimeException error) {
            if (failureEventReceived.compareAndSet(false, true)) {
                try {
                    targetObserver.onEvent(fallbackFailure(command, targetCancellation, error));
                } catch (RuntimeException callbackError) {
                    log.warn("typescript agent runtime failure event callback failed", callbackError);
                }
            }
            throw error;
        }
    }

    AgentRunEvent toEvent(JsonNode frame) {
        AgentRunEvent event = new AgentRunEvent();
        String frameType = text(frame, "type");
        String eventType = text(frame, "eventType");
        if (!StringUtils.hasText(eventType)) {
            eventType = "delta".equalsIgnoreCase(frameType)
                    ? "assistant.message.delta"
                    : "error".equalsIgnoreCase(frameType) ? "round.failed" : "agent.activity";
        }
        event.setEventType(eventType);
        event.setRunId(text(frame, "runId"));
        event.setRequestId(text(frame, "requestId"));
        event.setTraceId(text(frame, "traceId"));
        event.setSessionCode(text(frame, "sessionCode"));
        event.setRoundCode(text(frame, "roundCode"));
        event.setAgentCode(text(frame, "agentCode"));
        event.setAgentVersion(nullableInt(frame == null ? null : frame.get("agentVersion")));
        event.setAgentName(text(frame, "agentName"));
        event.setStatus(StringUtils.hasText(text(frame, "status"))
                ? text(frame, "status")
                : "error".equalsIgnoreCase(frameType) ? "FAILED" : null);
        event.setDelta(text(frame, "delta"));
        event.setMessage(text(frame, "message"));
        event.setTimestamp(parseInstant(text(frame, "timestamp")));
        copyObject(frame == null ? null : frame.get("ext"), event.getExt());
        copyIfPresent(frame, event.getExt(), "source", "activityCode", "activityType", "toolCode", "callId",
                "artifactCode", "artifactType", "summary");
        return event;
    }

    private AgentRunResult toResult(JsonNode node, AgentRunCommand command) {
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

    private AgentRunEvent fallbackFailure(AgentRunCommand command,
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
        event.setMessage(safeMessage(error));
        event.getExt().put("source", "OPENAI_AGENTS_TYPESCRIPT");
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
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        return node.isContainerNode() ? objectMapper.convertValue(node, Object.class) : node.asText();
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

    private String safeMessage(Throwable error) {
        String message = error == null || !StringUtils.hasText(error.getMessage())
                ? "TypeScript Agent execution failed"
                : error.getMessage();
        message = message.replaceAll("(?i)bearer\\s+[^\\s,;]+", "Bearer [REDACTED]");
        message = message.replaceAll("\\bsk-[A-Za-z0-9_-]{8,}\\b", "[REDACTED_OPENAI_KEY]");
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Integer nullableInt(JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }

    private int intValue(JsonNode node) {
        return node == null || node.isNull() ? 0 : node.asInt();
    }
}
