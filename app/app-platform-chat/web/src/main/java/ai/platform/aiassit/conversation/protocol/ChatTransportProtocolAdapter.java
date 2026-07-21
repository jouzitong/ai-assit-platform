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
import java.util.regex.Pattern;

@Component
public class ChatTransportProtocolAdapter {

    private static final int MAX_ERROR_DETAIL_LENGTH = 500;
    private static final int MAX_USER_MESSAGE_LENGTH = 200;
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*)(?:bearer\\s+)?[^\\s,;]+"
    );
    private static final Pattern CREDENTIAL_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)([\\\"']?(?:api[-_ ]?key|secret|access[-_ ]?token|refresh[-_ ]?token|token|password)"
                    + "[\\\"']?\\s*[:=]\\s*)[\\\"']?[^\\s,;\\\"'}]+[\\\"']?"
    );
    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(?i)\\bbearer\\s+[a-z0-9._~+/=-]+"
    );
    private static final Pattern OPENAI_KEY_PATTERN = Pattern.compile(
            "(?i)\\bsk-[a-z0-9_-]{4,}\\b"
    );
    private static final Pattern HAN_CHARACTER_PATTERN = Pattern.compile("\\p{IsHan}");

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
        if ("answer.delta".equals(type) || "assistant.message.delta".equals(type)) {
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
        // Provider 运行时的 round.* 只表示 Provider 自身结束，不能冒充浏览器协议终态。
        // 会话层会在持久化和收口完成后统一发布 complete/error/run.cancelled。
        if ("round.completed".equals(type)
                || "round.failed".equals(type)
                || "round.cancelled".equals(type)) {
            return List.of();
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
        put(activity, "startedAt", firstText(activity.get("startedAt"), ext.get("startedAt")));
        put(activity, "finishedAt", firstText(activity.get("finishedAt"), ext.get("finishedAt")));
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
        return failureError(event.getMessage(), event.getRequestId(), event.getSource(), event.getExt());
    }

    /** Builds the stable, client-safe error contract used by failed rounds and run recovery. */
    public Map<String, Object> failureError(String rawMessage,
                                            String traceId,
                                            String source,
                                            Map<String, Object> extensions) {
        Map<String, Object> error = new LinkedHashMap<>();
        Map<String, Object> ext = extensions == null ? Map.of() : extensions;
        Map<String, Object> nestedError = mapValue(ext.get("error"));
        String detail = firstText(
                nestedError.get("detail"), ext.get("detail"), ext.get("errorDetail"), rawMessage);
        String explicitUserMessage = firstText(nestedError.get("userMessage"), ext.get("userMessage"));
        String classificationMessage = String.join(" ",
                defaultValue(detail, ""),
                defaultValue(explicitUserMessage, ""));
        ErrorClassification classification = classifyError(classificationMessage, source);
        String explicitTraceId = firstText(nestedError.get("traceId"), ext.get("traceId"), traceId);

        error.put("code", defaultValue(firstText(
                nestedError.get("code"), ext.get("errorCode"), ext.get("code")), classification.code()));
        String userMessage = hasChinese(explicitUserMessage)
                ? explicitUserMessage
                : classification.userMessage();
        error.put("userMessage", limit(sanitize(userMessage), MAX_USER_MESSAGE_LENGTH));
        error.put("retryable", explicitRetryable(nestedError, ext, classification.retryable()));
        error.put("traceId", defaultValue(explicitTraceId, ""));
        error.put("detail", limit(sanitize(defaultValue(detail, "")), MAX_ERROR_DETAIL_LENGTH));
        return error;
    }

    private ErrorClassification classifyError(String message, String source) {
        String normalized = defaultValue(message, "").toLowerCase(Locale.ROOT);
        boolean agent = "AI_AGENT".equalsIgnoreCase(source)
                || containsAny(normalized, "ai agent", "python process", "agent provider");

        if (containsAny(normalized,
                "configuration missing", "config missing", "not configured", "configuration invalid",
                "required api", "required model", "script path", "配置缺失", "配置无效", "未配置")) {
            return new ErrorClassification(
                    "MODEL_CONFIG_INVALID", "模型服务配置不完整，请联系管理员检查配置", false);
        }
        if (containsAny(normalized,
                "api key", "apikey", "api_key", "credential", "unauthorized", "authentication failed",
                "invalid token", "token invalid", "access denied", "401", "403", "凭证", "认证失败")) {
            return new ErrorClassification(
                    "MODEL_CREDENTIAL_INVALID", "模型服务凭证无效，请联系管理员检查配置", false);
        }
        if (containsAny(normalized,
                "model not found", "model unavailable", "model is unavailable", "model disabled",
                "unsupported model", "invalid model", "unknown model", "模型不存在", "模型不可用", "模型已停用")) {
            return new ErrorClassification(
                    "MODEL_NOT_AVAILABLE", "当前模型不可用，请联系管理员检查配置", false);
        }
        if (containsAny(normalized, "rate limit", "too many requests", "429", "限流")) {
            return new ErrorClassification(
                    "MODEL_RATE_LIMITED", "模型服务当前繁忙，请稍后重试", true);
        }
        if (containsAny(normalized, "timeout", "timed out", "超时")) {
            return agent
                    ? new ErrorClassification("AI_AGENT_TIMEOUT", "AI Agent 执行超时，请稍后重试", true)
                    : new ErrorClassification("MODEL_TIMEOUT", "模型处理超时，请稍后重试", true);
        }
        if (containsAny(normalized,
                "connection", "connection reset", "connection refused", "network", "service unavailable",
                "temporarily unavailable", "bad gateway", "502", "503", "504", "连接失败", "网络异常")) {
            return new ErrorClassification(
                    "MODEL_CONNECTION_FAILED", "模型服务连接失败，请稍后重试", true);
        }
        if (agent) {
            return new ErrorClassification(
                    "AI_AGENT_EXECUTION_FAILED", "AI Agent 执行失败，请稍后重试", true);
        }
        if ("CONVERSATION".equalsIgnoreCase(source)
                || containsAny(normalized, "workflow", "process failed", "流程")) {
            return new ErrorClassification(
                    "WORKFLOW_EXECUTION_FAILED", "AI 处理流程暂时失败，请稍后重试", true);
        }
        return new ErrorClassification("CHAT_RUN_FAILED", "AI 处理失败，请稍后重试", true);
    }

    private boolean explicitRetryable(Map<String, Object> nestedError,
                                      Map<String, Object> ext,
                                      boolean fallback) {
        Object value = nestedError.containsKey("retryable")
                ? nestedError.get("retryable")
                : ext.get("retryable");
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            if ("true".equalsIgnoreCase(text.trim())) {
                return true;
            }
            if ("false".equalsIgnoreCase(text.trim())) {
                return false;
            }
        }
        return fallback;
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        sanitized = AUTHORIZATION_PATTERN.matcher(sanitized).replaceAll("$1***");
        sanitized = CREDENTIAL_ASSIGNMENT_PATTERN.matcher(sanitized).replaceAll("$1***");
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("Bearer ***");
        sanitized = OPENAI_KEY_PATTERN.matcher(sanitized).replaceAll("sk-***");
        return sanitized;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private boolean hasChinese(String value) {
        return StringUtils.hasText(value) && HAN_CHARACTER_PATTERN.matcher(value).find();
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
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

    private record ErrorClassification(String code, String userMessage, boolean retryable) {
    }
}
