package ai.platform.aiassit.conversation.workflow.support;

import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatActivityDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.enums.AiChatActorType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import ai.platform.aiassit.chat.history.enums.AiChatDisplayLevel;
import ai.platform.aiassit.chat.history.enums.AiChatMessageType;
import ai.platform.aiassit.chat.history.service.AiChatArtifactService;
import ai.platform.aiassit.chat.history.service.AiChatActivityService;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

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

    private final AiChatMessageService messageService;
    private final AiChatArtifactService artifactService;
    private final AiChatActivityService activityService;
    private final ObjectMapper objectMapper;

    public AgentConversationHistoryRecorder(AiChatMessageService messageService,
                                   AiChatArtifactService artifactService,
                                   AiChatActivityService activityService,
                                   ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.artifactService = artifactService;
        this.activityService = activityService;
        this.objectMapper = objectMapper;
    }

    public AiChatMessageDTO saveMessage(ConversationRuntimeContext context,
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
        AiChatMessageDTO message = new AiChatMessageDTO();
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
        AiChatMessageDTO created = messageService.add(message);

        List<AiChatMessageDTO> messages = new ArrayList<>(context.getOrCreateUserMessageContext().getSessionMessages());
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
    public Optional<AiChatMessageDTO> saveFailureMessage(ConversationRuntimeContext context,
                                                         String rawErrorMessage) {
        try {
            AiChatMessageDTO existing = findFailureMessage(context);
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
                    AiChatActorType.AI.name(),
                    AiChatMessageType.ERROR_MESSAGE.name(),
                    "",
                    AiChatContentFormat.PLAIN_TEXT.name(),
                    AiChatDisplayLevel.VISIBLE.name(),
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
     * 按事件时间线尽力持久化Agent 运行活动。
     *
     * <p>活动属于可观测数据，持久化故障不能中断 AI 回答或实时事件发布。
     * correlationCode 用于前端合并同一活动的开始/完成状态。</p>
     */
    public Optional<AiChatActivityDTO> saveActivity(ConversationRuntimeContext context,
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
            AiChatActivityDTO record = new AiChatActivityDTO();
            record.setActivityCode(generateCode("activity"));
            record.setSessionCode(context.getSession().getSessionCode());
            record.setRoundCode(context.getRound().getRoundCode());
            record.setUserId(context.getSession().getUserId());
            record.setAgentCode(truncate(firstText(
                    detail.get("agentCode"), activity.get("agentCode"), detail.get("rootAgentCode")), 64));
            record.setCorrelationCode(truncate(correlationCode, 128));
            record.setActivityType(truncate(defaultText(
                    firstText(activity.get("activityType"), detail.get("activityType")), "AI_AGENT_ACTIVITY"), 32));
            record.setActivityName(truncate(defaultText(firstText(
                    activity.get("activityName"), detail.get("activityName"), detail.get("toolName"), message), "AI 执行活动"), 128));
            record.setSource(truncate(defaultText(source, "AI_AGENT"), 64));
            record.setPhase(truncate(phase, 32));
            record.setStatus(truncate(defaultText(status, "RUNNING"), 32));
            record.setMessage(truncate(message, 512));
            record.setInputSummary(truncate(firstText(activity.get("inputSummary"), detail.get("inputSummary")), 1000));
            record.setOutputSummary(truncate(firstText(activity.get("outputSummary"), detail.get("outputSummary")), 1000));
            record.setDurationMs(longValue(activity.get("durationMs"), detail.get("durationMs")));
            record.setRequestId(truncate(context.getCommand() == null ? null : context.getCommand().getTraceId(), 128));
            record.setSeqNo(nextActivitySeqNo(context));
            record.setDetailJson(toJson(detail));
            return Optional.ofNullable(activityService.add(record));
        } catch (RuntimeException ex) {
            log.warn("AI activity persistence degraded, sessionCode={}, roundCode={}, source={}, phase={}, correlationCode={}",
                    sessionCode(context), roundCode(context), source, phase, correlationCode, ex);
            return Optional.empty();
        }
    }

    public AiChatArtifactDTO saveArtifact(ConversationRuntimeContext context,
                                          String artifactType,
                                          String stage,
                                          String title,
                                          Object content,
                                          String contentFormat,
                                          boolean visible,
                                          String status,
                                          String relatedMessageCode,
                                          Object ext) {
        AiChatArtifactDTO artifact = new AiChatArtifactDTO();
        artifact.setArtifactCode(generateCode("artifact"));
        artifact.setSessionCode(context.getSession().getSessionCode());
        artifact.setRoundCode(context.getRound() == null ? null : context.getRound().getRoundCode());
        artifact.setUserId(context.getSession().getUserId());
        artifact.setRelatedMessageCode(relatedMessageCode);
        artifact.setArtifactType(artifactType);
        artifact.setStage(stage);
        artifact.setProducerType(visible ? AiChatActorType.AI.name() : AiChatActorType.SYSTEM.name());
        artifact.setVisibleFlag(visible);
        artifact.setTitle(title);
        artifact.setContent(stringifyContent(content));
        artifact.setContentFormat(contentFormat);
        artifact.setStatus(status);
        artifact.setSeqNo(nextArtifactSeqNo(context));
        artifact.setExtJson(toJson(ext));
        AiChatArtifactDTO created = artifactService.add(artifact);

        List<AiChatArtifactDTO> artifacts = new ArrayList<>(context.getSessionArtifacts());
        artifacts.add(created);
        context.setSessionArtifacts(artifacts);
        return created;
    }

    public String defaultDisplayLevel(boolean visible) {
        return visible ? AiChatDisplayLevel.VISIBLE.name() : AiChatDisplayLevel.COLLAPSIBLE.name();
    }

    public String defaultContentFormat(String contentFormat) {
        return contentFormat == null ? AiChatContentFormat.PLAIN_TEXT.name() : contentFormat;
    }

    public String defaultActorType(String actorType) {
        return actorType == null ? AiChatActorType.SYSTEM.name() : actorType;
    }

    private int nextMessageSortNo(ConversationRuntimeContext context) {
        List<AiChatMessageDTO> messages = context.getOrCreateUserMessageContext().getSessionMessages();
        return CollectionUtils.isEmpty(messages) ? 1 : messages.size() + 1;
    }

    private int nextArtifactSeqNo(ConversationRuntimeContext context) {
        return CollectionUtils.isEmpty(context.getSessionArtifacts()) ? 1 : context.getSessionArtifacts().size() + 1;
    }

    private int nextActivitySeqNo(ConversationRuntimeContext context) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(context.getSession().getSessionCode());
        query.setRoundCode(context.getRound().getRoundCode());
        return activityService.queryAll(query).size() + 1;
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

    private AiChatMessageDTO findFailureMessage(ConversationRuntimeContext context) {
        if (context == null || context.getOrCreateUserMessageContext().getSessionMessages() == null) {
            return null;
        }
        String currentRoundCode = roundCode(context);
        return context.getOrCreateUserMessageContext().getSessionMessages().stream()
                .filter(message -> message != null && currentRoundCode != null
                        && currentRoundCode.equals(message.getRoundCode()))
                .filter(message -> AiChatMessageType.ERROR_MESSAGE.name().equalsIgnoreCase(message.getMessageType()))
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
