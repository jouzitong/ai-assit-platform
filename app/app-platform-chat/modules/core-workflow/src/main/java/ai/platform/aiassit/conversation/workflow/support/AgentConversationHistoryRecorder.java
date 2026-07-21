package ai.platform.aiassit.conversation.workflow.support;

import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationArtifactDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationActivityDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import ai.platform.aiassit.conversation.data.entity.req.ConversationHistoryQueryRequest;
import ai.platform.aiassit.conversation.data.enums.ConversationActorType;
import ai.platform.aiassit.conversation.data.enums.ConversationArtifactType;
import ai.platform.aiassit.conversation.data.enums.ConversationContentFormat;
import ai.platform.aiassit.conversation.data.enums.ConversationDisplayLevel;
import ai.platform.aiassit.conversation.data.enums.ConversationMessageType;
import ai.platform.aiassit.conversation.data.service.ConversationArtifactService;
import ai.platform.aiassit.conversation.data.service.ConversationActivityService;
import ai.platform.aiassit.conversation.data.service.ConversationMessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Slf4j
public class AgentConversationHistoryRecorder {

    private static final int MAX_ERROR_DETAIL_LENGTH = 500;
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

    private final ConversationMessageService messageService;
    private final ConversationArtifactService artifactService;
    private final ConversationActivityService activityService;
    private final ObjectMapper objectMapper;

    public AgentConversationHistoryRecorder(ConversationMessageService messageService,
                                   ConversationArtifactService artifactService,
                                   ConversationActivityService activityService,
                                   ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.artifactService = artifactService;
        this.activityService = activityService;
        this.objectMapper = objectMapper;
    }

    public ConversationMessageDTO saveMessage(ConversationRuntimeContext context,
                                        String roundCode,
                                        String role,
                                        String actorType,
                                        String messageType,
                                        String content,
                                        String contentFormat,
                                        String displayLevel,
                                        String status,
                                        String parentMessageCode,
                                        String sourceMessageCode,
                                        Object ext) {
        ConversationMessageDTO message = new ConversationMessageDTO();
        message.setMessageCode(generateCode("msg"));
        message.setRoundCode(roundCode);
        message.setSessionCode(context.getSession().getSessionCode());
        message.setRole(role);
        message.setActorType(actorType);
        message.setMessageType(messageType);
        message.setContent(content);
        message.setContentFormat(contentFormat);
        message.setDisplayLevel(displayLevel);
        message.setStatus(status);
        message.setParentMessageCode(parentMessageCode);
        message.setSourceMessageCode(sourceMessageCode);
        message.setSortNo(nextMessageSortNo(context));
        message.setExtJson(toJson(ext));
        ConversationMessageDTO created = messageService.add(message);

        List<ConversationMessageDTO> messages = new ArrayList<>(context.getOrCreateUserMessageContext().getSessionMessages());
        messages.add(created);
        context.getOrCreateUserMessageContext().setSessionMessages(messages);
        return created;
    }

    /**
     * 尽力保存当前轮次的安全失败快照，供历史会话恢复错误卡片。
     *
     * <p>消息正文保持为空，错误展示信息仅存放在已脱敏的 extJson.error 中；
     * 任何查询、序列化或写库异常都不会覆盖原 Agent 运行异常。</p>
     */
    public Optional<ConversationMessageDTO> saveFailureMessage(ConversationRuntimeContext context,
                                                         String rawErrorMessage) {
        try {
            ConversationMessageDTO existing = findFailureMessage(context);
            if (existing != null) {
                return Optional.of(existing);
            }
            String currentMessageCode = context.getOrCreateUserMessageContext().getCurrentMessage() == null
                    ? null
                    : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode();
            Map<String, Object> ext = Map.of("error", safeFailureError(context, rawErrorMessage));
            return Optional.ofNullable(saveMessage(
                    context,
                    context.getRound().getRoundCode(),
                    "ASSISTANT",
                    ConversationActorType.AI.name(),
                    ConversationMessageType.ERROR_MESSAGE.name(),
                    "",
                    ConversationContentFormat.PLAIN_TEXT.name(),
                    ConversationDisplayLevel.VISIBLE.name(),
                    "FAILED",
                    currentMessageCode,
                    currentMessageCode,
                    ext
            ));
        } catch (RuntimeException ex) {
            log.warn("AI failure message persistence degraded, sessionCode={}, roundCode={}, traceId={}",
                    sessionCode(context), roundCode(context), traceId(context), ex);
            return Optional.empty();
        }
    }

    /**
     * 按活动生命周期尽力持久化 Agent 运行活动。
     *
     * <p>活动属于可观测数据，持久化故障不能中断 AI 回答或实时事件发布。
     * correlationCode 相同时，开始、更新、完成或失败事件更新同一条记录。</p>
     */
    public Optional<ConversationActivityDTO> saveActivity(ConversationRuntimeContext context,
                                                    String source,
                                                    String phase,
                                                    String message,
                                                    String status,
                                                    Map<String, Object> ext) {
        Map<String, Object> detail = ext == null ? Map.of() : new LinkedHashMap<>(ext);
        Map<String, Object> activity = mapValue(detail.get("activity"));
        String correlationCode = firstText(
                activity.get("activityCode"), detail.get("activityCode"), detail.get("callId"),
                activity.get("callId"), detail.get("activity"), detail.get("toolName"));
        try {
            ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
            query.setSessionCode(context.getSession().getSessionCode());
            query.setRoundCode(context.getRound().getRoundCode());
            List<ConversationActivityDTO> currentActivities = activityService.queryAll(query);
            ConversationActivityDTO existing = findActivity(currentActivities, correlationCode);
            ConversationActivityDTO record = existing == null ? new ConversationActivityDTO() : existing;
            if (existing == null) {
                record.setActivityCode(generateCode("activity"));
                record.setSessionCode(context.getSession().getSessionCode());
                record.setRoundCode(context.getRound().getRoundCode());
                record.setUserId(context.getSession().getUserId());
                record.setSeqNo(currentActivities.size() + 1);
            }
            record.setAgentCode(truncate(firstText(
                    detail.get("agentCode"), activity.get("agentCode"), detail.get("rootAgentCode"),
                    record.getAgentCode()), 64));
            record.setCorrelationCode(truncate(correlationCode, 128));
            record.setActivityType(truncate(defaultText(
                    firstText(activity.get("activityType"), detail.get("activityType"), record.getActivityType()),
                    "AI_AGENT_ACTIVITY"), 32));
            record.setActivityName(truncate(defaultText(firstText(
                    activity.get("activityName"), detail.get("activityName"), detail.get("toolName"),
                    record.getActivityName(), message), "AI 执行活动"), 128));
            record.setSource(truncate(defaultText(firstText(source, record.getSource()), "AI_AGENT"), 64));
            record.setPhase(truncate(firstText(phase, record.getPhase()), 32));
            record.setStatus(truncate(defaultText(status, "RUNNING"), 32));
            record.setMessage(truncate(firstText(message, record.getMessage()), 512));
            record.setInputSummary(firstText(
                    activity.get("inputSummary"), detail.get("inputSummary"), record.getInputSummary()));
            record.setOutputSummary(firstText(
                    activity.get("outputSummary"), detail.get("outputSummary"), record.getOutputSummary()));
            updateActivityTiming(record, status, activity, detail);
            record.setRequestId(truncate(firstText(
                    context.getCommand() == null ? null : context.getCommand().getTraceId(), record.getRequestId()), 128));
            record.setDetailJson(toJson(mergeDetail(record.getDetailJson(), detail)));
            ConversationActivityDTO saved = existing == null
                    ? activityService.add(record)
                    : activityService.update(existing.getId(), record);
            return Optional.ofNullable(saved);
        } catch (RuntimeException ex) {
            log.warn("AI activity persistence degraded, sessionCode={}, roundCode={}, source={}, phase={}, correlationCode={}",
                    sessionCode(context), roundCode(context), source, phase, correlationCode, ex);
            return Optional.empty();
        }
    }

    public ConversationArtifactDTO saveArtifact(ConversationRuntimeContext context,
                                                 ConversationArtifactType artifactType,
                                                 String stage,
                                                 String title,
                                                 Object content,
                                                 String contentFormat,
                                                 Object ext) {
        ConversationArtifactDTO artifact = new ConversationArtifactDTO();
        artifact.setArtifactCode(generateCode("artifact"));
        artifact.setRoundCode(context.getRound().getRoundCode());
        artifact.setArtifactType(artifactType.name());
        artifact.setStage(stage);
        artifact.setTitle(title);
        artifact.setContent(stringifyContent(content));
        artifact.setContentFormat(contentFormat);
        artifact.setSeqNo(nextArtifactSeqNo(context));
        artifact.setExtJson(toJson(ext));
        ConversationArtifactDTO created = artifactService.add(artifact);

        List<ConversationArtifactDTO> artifacts = new ArrayList<>(context.getSessionArtifacts());
        artifacts.add(created);
        context.setSessionArtifacts(artifacts);
        return created;
    }

    public String defaultDisplayLevel(boolean visible) {
        return visible ? ConversationDisplayLevel.VISIBLE.name() : ConversationDisplayLevel.COLLAPSIBLE.name();
    }

    public String defaultContentFormat(String contentFormat) {
        return contentFormat == null ? ConversationContentFormat.PLAIN_TEXT.name() : contentFormat;
    }

    public String defaultActorType(String actorType) {
        return actorType == null ? ConversationActorType.SYSTEM.name() : actorType;
    }

    private int nextMessageSortNo(ConversationRuntimeContext context) {
        List<ConversationMessageDTO> messages = context.getOrCreateUserMessageContext().getSessionMessages();
        return CollectionUtils.isEmpty(messages) ? 1 : messages.size() + 1;
    }

    private int nextArtifactSeqNo(ConversationRuntimeContext context) {
        return CollectionUtils.isEmpty(context.getSessionArtifacts()) ? 1 : context.getSessionArtifacts().size() + 1;
    }

    private ConversationActivityDTO findActivity(List<ConversationActivityDTO> activities, String correlationCode) {
        if (correlationCode == null || correlationCode.isBlank() || CollectionUtils.isEmpty(activities)) {
            return null;
        }
        for (int index = activities.size() - 1; index >= 0; index--) {
            ConversationActivityDTO candidate = activities.get(index);
            if (candidate != null && correlationCode.equals(candidate.getCorrelationCode())) {
                return candidate;
            }
        }
        return null;
    }

    private void updateActivityTiming(ConversationActivityDTO record,
                                      String status,
                                      Map<String, Object> activity,
                                      Map<String, Object> detail) {
        Instant explicitStartedAt = instantValue(activity.get("startedAt"), detail.get("startedAt"));
        Instant eventAt = instantValue(activity.get("timestamp"), detail.get("timestamp"));
        if (record.getStartedAt() == null && (isRunning(status) || explicitStartedAt != null)) {
            record.setStartedAt(explicitStartedAt != null ? explicitStartedAt
                    : eventAt != null ? eventAt : Instant.now());
        }
        Long suppliedDuration = longValue(activity.get("durationMs"), detail.get("durationMs"));
        if (suppliedDuration != null) {
            record.setDurationMs(suppliedDuration);
        }
        if (!isTerminal(status)) {
            return;
        }
        Instant finishedAt = instantValue(activity.get("finishedAt"), detail.get("finishedAt"));
        record.setFinishedAt(finishedAt != null ? finishedAt : eventAt != null ? eventAt : Instant.now());
        if (record.getDurationMs() == null && record.getStartedAt() != null) {
            record.setDurationMs(Math.max(0L, Duration.between(record.getStartedAt(), record.getFinishedAt()).toMillis()));
        }
    }

    private boolean isRunning(String status) {
        return status == null || status.isBlank()
                || "RUNNING".equalsIgnoreCase(status)
                || "STARTED".equalsIgnoreCase(status)
                || "PENDING".equalsIgnoreCase(status);
    }

    private boolean isTerminal(String status) {
        if (status == null) {
            return false;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "SUCCEEDED", "COMPLETE", "COMPLETED", "DONE", "FAILED", "ERROR",
                    "CANCELLED", "CANCELED" -> true;
            default -> false;
        };
    }

    private Instant instantValue(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value instanceof Instant instant) {
                return instant;
            }
            if (value instanceof Number number) {
                return Instant.ofEpochMilli(number.longValue());
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Instant.parse(text.trim());
                } catch (RuntimeException ignored) {
                    // Try the next compatible value.
                }
            }
        }
        return null;
    }

    private Map<String, Object> mergeDetail(String currentJson, Map<String, Object> incoming) {
        Map<String, Object> merged = new LinkedHashMap<>(jsonMap(currentJson));
        Map<String, Object> currentActivity = mapValue(merged.get("activity"));
        Map<String, Object> incomingActivity = mapValue(incoming.get("activity"));
        merged.putAll(incoming);
        if (!currentActivity.isEmpty() || !incomingActivity.isEmpty()) {
            Map<String, Object> mergedActivity = new LinkedHashMap<>(currentActivity);
            mergedActivity.putAll(incomingActivity);
            merged.put("activity", mergedActivity);
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            Object decoded = objectMapper.readValue(value, Object.class);
            return decoded instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String firstText(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value instanceof String text && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private Long longValue(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Long.parseLong(text.trim());
                } catch (NumberFormatException ignored) {
                    // Try the next compatible value.
                }
            }
        }
        return null;
    }

    private String stringifyContent(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String str) {
            return str;
        }
        return toJson(content);
    }

    private String toJson(Object ext) {
        if (ext == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ext);
        } catch (JsonProcessingException ex) {
            return String.valueOf(ext);
        }
    }

    private String generateCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private ConversationMessageDTO findFailureMessage(ConversationRuntimeContext context) {
        if (context == null || context.getOrCreateUserMessageContext().getSessionMessages() == null) {
            return null;
        }
        String currentRoundCode = roundCode(context);
        return context.getOrCreateUserMessageContext().getSessionMessages().stream()
                .filter(message -> message != null && currentRoundCode != null
                        && currentRoundCode.equals(message.getRoundCode()))
                .filter(message -> ConversationMessageType.ERROR_MESSAGE.name().equalsIgnoreCase(message.getMessageType()))
                .filter(message -> "FAILED".equalsIgnoreCase(message.getStatus()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> safeFailureError(ConversationRuntimeContext context, String rawErrorMessage) {
        FailureClassification classification = classifyFailure(rawErrorMessage);
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", classification.code());
        error.put("userMessage", classification.userMessage());
        error.put("retryable", classification.retryable());
        error.put("traceId", defaultText(traceId(context), ""));
        error.put("detail", truncateWithEllipsis(sanitize(rawErrorMessage), MAX_ERROR_DETAIL_LENGTH));
        return error;
    }

    private FailureClassification classifyFailure(String rawErrorMessage) {
        String normalized = defaultText(rawErrorMessage, "").toLowerCase(Locale.ROOT);
        boolean agent = containsAny(normalized, "ai agent", "python process", "agent provider");
        if (containsAny(normalized,
                "configuration missing", "config missing", "not configured", "configuration invalid",
                "required api", "required model", "script path", "配置缺失", "配置无效", "未配置")) {
            return new FailureClassification(
                    "MODEL_CONFIG_INVALID", "模型服务配置不完整，请联系管理员检查配置", false);
        }
        if (containsAny(normalized,
                "api key", "apikey", "api_key", "credential", "unauthorized", "authentication failed",
                "invalid token", "token invalid", "access denied", "401", "403", "凭证", "认证失败")) {
            return new FailureClassification(
                    "MODEL_CREDENTIAL_INVALID", "模型服务凭证无效，请联系管理员检查配置", false);
        }
        if (containsAny(normalized,
                "model not found", "model unavailable", "model is unavailable", "model disabled",
                "unsupported model", "invalid model", "unknown model", "模型不存在", "模型不可用", "模型已停用")) {
            return new FailureClassification(
                    "MODEL_NOT_AVAILABLE", "当前模型不可用，请联系管理员检查配置", false);
        }
        if (containsAny(normalized, "rate limit", "too many requests", "429", "限流")) {
            return new FailureClassification(
                    "MODEL_RATE_LIMITED", "模型服务当前繁忙，请稍后重试", true);
        }
        if (containsAny(normalized, "timeout", "timed out", "超时")) {
            return agent
                    ? new FailureClassification("AI_AGENT_TIMEOUT", "AI Agent 执行超时，请稍后重试", true)
                    : new FailureClassification("MODEL_TIMEOUT", "模型处理超时，请稍后重试", true);
        }
        if (containsAny(normalized,
                "connection", "network", "service unavailable", "temporarily unavailable", "bad gateway",
                "502", "503", "504", "连接失败", "网络异常")) {
            return new FailureClassification(
                    "MODEL_CONNECTION_FAILED", "模型服务连接失败，请稍后重试", true);
        }
        if (agent) {
            return new FailureClassification(
                    "AI_AGENT_EXECUTION_FAILED", "AI Agent 执行失败，请稍后重试", true);
        }
        return new FailureClassification(
                "WORKFLOW_EXECUTION_FAILED", "AI 处理流程暂时失败，请稍后重试", true);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        sanitized = AUTHORIZATION_PATTERN.matcher(sanitized).replaceAll("$1***");
        sanitized = CREDENTIAL_ASSIGNMENT_PATTERN.matcher(sanitized).replaceAll("$1***");
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("Bearer ***");
        return OPENAI_KEY_PATTERN.matcher(sanitized).replaceAll("sk-***");
    }

    private String truncateWithEllipsis(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String sessionCode(ConversationRuntimeContext context) {
        return context == null || context.getSession() == null ? null : context.getSession().getSessionCode();
    }

    private String roundCode(ConversationRuntimeContext context) {
        return context == null || context.getRound() == null ? null : context.getRound().getRoundCode();
    }

    private String traceId(ConversationRuntimeContext context) {
        return context == null || context.getCommand() == null ? null : context.getCommand().getTraceId();
    }

    private record FailureClassification(String code, String userMessage, boolean retryable) {
    }
}
