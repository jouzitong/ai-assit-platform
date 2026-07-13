package ai.platform.aiassit.conversation.protocol;

import ai.platform.aiassit.conversation.protocol.dto.ChatEventEnvelope;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ChatTransportProtocolAdapter {

    public List<ChatEventEnvelope> adapt(ConversationQueryStreamEvent event) {
        if (event == null) {
            return List.of();
        }
        List<EventProjection> projections = project(event);
        List<ChatEventEnvelope> envelopes = new ArrayList<>(projections.size());
        for (int i = 0; i < projections.size(); i++) {
            EventProjection projection = projections.get(i);
            ChatEventEnvelope envelope = baseEnvelope(event);
            envelope.setEventId(projectedEventId(event.getEventId(), i, projections.size()));
            envelope.setEventType(projection.eventType());
            envelope.setPayload(projection.payload());
            envelopes.add(envelope);
        }
        return envelopes;
    }

    private List<EventProjection> project(ConversationQueryStreamEvent event) {
        String type = normalize(event.getEventType());
        if ("run.accepted".equals(type)) {
            return List.of(projection("run.accepted", runPayload(event)));
        }
        if ("run.started".equals(type)) {
            return List.of(projection("run.started", runPayload(event)));
        }
        if ("run.cancelled".equals(type)) {
            return List.of(projection("round.cancelled", roundTerminalPayload(event, "cancelled")));
        }
        if ("error".equals(type)) {
            return List.of(projection("round.failed", roundTerminalPayload(event, "failed")));
        }
        if ("clarification".equals(type)) {
            return List.of(projection("assistant.input_required", inputRequiredPayload(event)));
        }
        if ("complete".equals(type)) {
            return List.of(
                    projection("thinking.completed", thinkingPayload(event, "completed")),
                    projection("round.completed", roundTerminalPayload(event, "completed"))
            );
        }
        if ("answer_delta".equals(type)) {
            return List.of(projection("assistant.message.delta", answerPayload(event, event.getDelta(), true)));
        }
        if ("answer".equals(type)) {
            List<EventProjection> result = new ArrayList<>();
            if (event.getExt() != null && StringUtils.hasText(stringValue(event.getExt().get("codeRef")))) {
                result.add(projection("artifacts.build", artifactPayload(event)));
            }
            result.add(projection("assistant.message.delta", answerPayload(event, event.getAnswer(), false)));
            return result;
        }
        if ("progress".equals(type) && "CONVERSATION".equalsIgnoreCase(event.getSource())
                && "STARTED".equalsIgnoreCase(event.getPhase())) {
            return List.of(
                    projection("session.initialized", sessionPayload(event)),
                    projection("round.initialized", roundInitializedPayload(event)),
                    projection("assistant.started", assistantPayload(event, "running")),
                    projection("thinking.started", thinkingPayload(event, "running"))
            );
        }
        if ("progress".equals(type)) {
            return List.of(projection("thinking.updated", thinkingUpdatedPayload(event)));
        }
        return List.of(projection(type, genericPayload(event)));
    }

    private ChatEventEnvelope baseEnvelope(ConversationQueryStreamEvent event) {
        ChatEventEnvelope envelope = new ChatEventEnvelope();
        envelope.setRunId(event.getRunId());
        envelope.setRequestId(event.getRequestId());
        envelope.setSessionCode(event.getSessionCode());
        envelope.setRoundCode(event.getRoundCode());
        envelope.setTimestamp(event.getTimestamp() == null
                ? Instant.now().toString()
                : Instant.ofEpochMilli(event.getTimestamp()).toString());
        return envelope;
    }

    private Map<String, Object> runPayload(ConversationQueryStreamEvent event) {
        Map<String, Object> run = new LinkedHashMap<>();
        put(run, "id", event.getRunId());
        put(run, "status", lower(event.getStatus()));
        put(run, "message", event.getMessage());
        return Map.of("run", run);
    }

    private Map<String, Object> sessionPayload(ConversationQueryStreamEvent event) {
        Map<String, Object> conversation = new LinkedHashMap<>();
        conversation.put("schemaVersion", "chat-round.v4");
        put(conversation, "id", event.getSessionCode());
        put(conversation, "sessionCode", event.getSessionCode());
        put(conversation, "title", event.getSessionName());
        mergeMap(conversation, extMap(event, "conversation"));
        return Map.of("conversation", conversation);
    }

    private Map<String, Object> roundInitializedPayload(ConversationQueryStreamEvent event) {
        Map<String, Object> round = new LinkedHashMap<>();
        put(round, "id", event.getRoundCode());
        put(round, "roundCode", event.getRoundCode());
        put(round, "status", "pending");
        Object userMessage = event.getExt() == null ? null : event.getExt().get("userMessage");
        if (userMessage != null) {
            round.put("userMessage", userMessage);
        }
        round.put("assistant", assistant(event, "pending"));
        mergeMap(round, extMap(event, "round"));
        return Map.of("round", round);
    }

    private Map<String, Object> assistantPayload(ConversationQueryStreamEvent event, String status) {
        return Map.of("assistant", assistant(event, status));
    }

    private Map<String, Object> assistant(ConversationQueryStreamEvent event, String status) {
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("id", "assistant-" + defaultValue(event.getRoundCode(), event.getRunId()));
        assistant.put("role", "assistant");
        assistant.put("status", status);
        assistant.put("messages", List.of());
        assistant.put("artifacts", List.of());
        return assistant;
    }

    private Map<String, Object> thinkingPayload(ConversationQueryStreamEvent event, String status) {
        Map<String, Object> thinking = new LinkedHashMap<>();
        thinking.put("id", "thinking-" + defaultValue(event.getRoundCode(), event.getRunId()));
        thinking.put("type", "roundThinking");
        thinking.put("summary", "思考过程");
        thinking.put("status", status);
        thinking.put("statusText", defaultValue(event.getMessage(), "思考中"));
        thinking.put("query", query(event));
        return Map.of("thinking", thinking);
    }

    private Map<String, Object> thinkingUpdatedPayload(ConversationQueryStreamEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        boolean activityProgress = "ACTIVITY".equalsIgnoreCase(event.getProgressType());
        payload.put("action", activityProgress
                ? "activity.updated"
                : normalize(event.getSource()) + "." + normalize(event.getPhase()));
        Map<String, Object> thinking = new LinkedHashMap<>();
        thinking.put("status", lower(defaultValue(event.getStatus(), "RUNNING")));
        thinking.put("statusText", event.getMessage());
        thinking.put("query", query(event));
        payload.put("thinking", thinking);
        payload.put("source", event.getSource());
        payload.put("phase", event.getPhase());
        if (StringUtils.hasText(event.getProgressType())) {
            payload.put("progressType", event.getProgressType());
        }
        if (event.getExt() != null) {
            payload.putAll(event.getExt());
        }
        if (activityProgress) {
            payload.put("activity", activityPayload(event));
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> activityPayload(ConversationQueryStreamEvent event) {
        Map<String, Object> ext = event.getExt() == null ? Map.of() : event.getExt();
        Map<String, Object> activity = ext.get("activity") instanceof Map<?, ?> value
                ? new LinkedHashMap<>((Map<String, Object>) value)
                : new LinkedHashMap<>();
        String legacyActivity = ext.get("activity") instanceof String value ? value : null;
        String code = firstText(activity.get("activityCode"), ext.get("activityCode"), ext.get("callId"),
                activity.get("callId"), legacyActivity, ext.get("toolName"));
        put(activity, "id", code);
        put(activity, "activityCode", code);
        put(activity, "activityType", firstText(activity.get("activityType"), ext.get("activityType")));
        put(activity, "activityName", firstText(activity.get("activityName"), ext.get("activityName"),
                ext.get("toolName"), event.getMessage()));
        put(activity, "title", firstText(activity.get("title"), activity.get("activityName"), event.getMessage()));
        put(activity, "description", firstText(activity.get("description"), event.getMessage()));
        put(activity, "source", event.getSource());
        put(activity, "phase", event.getPhase());
        put(activity, "status", lower(event.getStatus()));
        put(activity, "inputSummary", firstText(activity.get("inputSummary"), ext.get("inputSummary")));
        put(activity, "outputSummary", firstText(activity.get("outputSummary"), ext.get("outputSummary")));
        if (ext.get("durationMs") != null) {
            activity.putIfAbsent("durationMs", ext.get("durationMs"));
        }
        return activity;
    }

    private Map<String, Object> answerPayload(ConversationQueryStreamEvent event, String content, boolean append) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", "assistant-message-" + defaultValue(event.getRoundCode(), event.getRunId()));
        message.put("role", "assistant");
        message.put("append", append);
        message.put("content", List.of(Map.of(
                "type", contentType(event),
                "text", defaultValue(content, "")
        )));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        if (event.getExt() != null && !event.getExt().isEmpty()) {
            payload.put("ext", event.getExt());
        }
        return payload;
    }

    private Map<String, Object> artifactPayload(ConversationQueryStreamEvent event) {
        Map<String, Object> artifact = new LinkedHashMap<>();
        put(artifact, "codeRef", event.getExt().get("codeRef"));
        put(artifact, "artifactType", event.getExt().get("artifactType"));
        put(artifact, "contentFormat", event.getExt().get("contentFormat"));
        put(artifact, "title", event.getExt().get("title"));
        put(artifact, "status", lower(event.getStatus()));
        return Map.of("artifact", artifact);
    }

    private Map<String, Object> roundTerminalPayload(ConversationQueryStreamEvent event, String status) {
        Map<String, Object> round = new LinkedHashMap<>();
        put(round, "id", event.getRoundCode());
        put(round, "roundCode", event.getRoundCode());
        round.put("status", status);
        if (StringUtils.hasText(event.getAnswer())) {
            round.put("assistant", Map.of(
                    "id", "assistant-" + defaultValue(event.getRoundCode(), event.getRunId()),
                    "role", "assistant",
                    "status", status,
                    "messages", List.of(answerPayload(event, event.getAnswer(), false).get("message")),
                    "artifacts", List.of()
            ));
        }
        put(round, "message", event.getMessage());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("round", round);
        if ("failed".equals(status)) {
            payload.put("error", errorPayload(event));
        }
        return payload;
    }

    private Map<String, Object> errorPayload(ConversationQueryStreamEvent event) {
        Map<String, Object> error = new LinkedHashMap<>();
        Map<String, Object> ext = event.getExt() == null ? Map.of() : event.getExt();
        error.put("code", defaultValue(firstText(ext.get("errorCode"), ext.get("code")), "CHAT_RUN_FAILED"));
        error.put("userMessage", defaultValue(firstText(ext.get("userMessage")), "AI 处理失败，请稍后重试"));
        Object retryable = ext.get("retryable");
        error.put("retryable", retryable instanceof Boolean value ? value : Boolean.TRUE);
        put(error, "traceId", event.getRequestId());
        return error;
    }

    private Map<String, Object> inputRequiredPayload(ConversationQueryStreamEvent event) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("message", event.getMessage());
        input.put("query", query(event));
        return Map.of("input", input);
    }

    private Map<String, Object> genericPayload(ConversationQueryStreamEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        put(payload, "source", event.getSource());
        put(payload, "phase", event.getPhase());
        put(payload, "status", lower(event.getStatus()));
        put(payload, "message", event.getMessage());
        if (event.getExt() != null) {
            payload.putAll(event.getExt());
        }
        return payload;
    }

    private Map<String, Object> query(ConversationQueryStreamEvent event) {
        Map<String, Object> query = new LinkedHashMap<>();
        put(query, "sessionCode", event.getSessionCode());
        put(query, "roundCode", event.getRoundCode());
        return query;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extMap(ConversationQueryStreamEvent event, String key) {
        if (event.getExt() == null || !(event.getExt().get(key) instanceof Map<?, ?> value)) {
            return Map.of();
        }
        return (Map<String, Object>) value;
    }

    private void mergeMap(Map<String, Object> target, Map<String, Object> values) {
        if (values != null) {
            target.putAll(values);
        }
    }

    private String projectedEventId(String sourceId, int index, int size) {
        String id = StringUtils.hasText(sourceId) ? sourceId : "0";
        return size == 1 ? id : id + "." + (index + 1);
    }

    private EventProjection projection(String eventType, Map<String, Object> payload) {
        return new EventProjection(eventType, payload);
    }

    private String contentType(ConversationQueryStreamEvent event) {
        Object format = event.getExt() == null ? null : event.getExt().get("contentFormat");
        return "JSON".equalsIgnoreCase(stringValue(format)) ? "render" : "text";
    }

    private String normalize(String value) {
        return value == null ? "message" : value.trim().toLowerCase(Locale.ROOT).replace('_', '.');
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            String text = stringValue(value);
            if (StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof String text) || StringUtils.hasText(text))) {
            target.put(key, value);
        }
    }

    private record EventProjection(String eventType, Map<String, Object> payload) {
    }
}
