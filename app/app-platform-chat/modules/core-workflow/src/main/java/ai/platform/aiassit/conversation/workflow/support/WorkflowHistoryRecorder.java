package ai.platform.aiassit.conversation.workflow.support;

import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatActivityDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.enums.AiChatActorType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import ai.platform.aiassit.chat.history.enums.AiChatDisplayLevel;
import ai.platform.aiassit.chat.history.service.AiChatArtifactService;
import ai.platform.aiassit.chat.history.service.AiChatActivityService;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class WorkflowHistoryRecorder {

    private final AiChatMessageService messageService;
    private final AiChatArtifactService artifactService;
    private final AiChatActivityService activityService;
    private final ObjectMapper objectMapper;

    public WorkflowHistoryRecorder(AiChatMessageService messageService,
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

    /** 按事件时间线持久化节点内活动，correlationCode 用于前端合并同一活动的开始/完成状态。 */
    public AiChatActivityDTO saveActivity(ConversationRuntimeContext context,
                                          String source,
                                          String phase,
                                          String message,
                                          String status,
                                          Map<String, Object> ext) {
        Map<String, Object> detail = ext == null ? Map.of() : new LinkedHashMap<>(ext);
        Map<String, Object> activity = mapValue(detail.get("activity"));
        AiChatActivityDTO record = new AiChatActivityDTO();
        record.setActivityCode(generateCode("activity"));
        record.setSessionCode(context.getSession().getSessionCode());
        record.setRoundCode(context.getRound().getRoundCode());
        record.setUserId(context.getSession().getUserId());
        record.setNodeCode(truncate(firstText(detail.get("nodeCode"), activity.get("nodeCode")), 64));
        record.setCorrelationCode(truncate(firstText(
                activity.get("activityCode"), detail.get("activityCode"), detail.get("callId"),
                activity.get("callId"), detail.get("activity"), detail.get("toolName")), 128));
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
        return activityService.add(record);
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
}
