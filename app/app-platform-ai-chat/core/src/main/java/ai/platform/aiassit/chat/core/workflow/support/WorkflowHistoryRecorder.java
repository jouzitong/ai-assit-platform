package ai.platform.aiassit.chat.core.workflow.support;

import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.enums.AiChatActorType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import ai.platform.aiassit.chat.history.enums.AiChatDisplayLevel;
import ai.platform.aiassit.chat.history.service.AiChatArtifactService;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class WorkflowHistoryRecorder {

    private final AiChatMessageService messageService;
    private final AiChatArtifactService artifactService;
    private final ObjectMapper objectMapper;

    public WorkflowHistoryRecorder(AiChatMessageService messageService,
                                   AiChatArtifactService artifactService,
                                   ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.artifactService = artifactService;
        this.objectMapper = objectMapper;
    }

    public AiChatMessageDTO saveMessage(WorkflowContext context,
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

        List<AiChatMessageDTO> messages = new ArrayList<>(context.getSessionMessages());
        messages.add(created);
        context.setSessionMessages(messages);
        return created;
    }

    public AiChatArtifactDTO saveArtifact(WorkflowContext context,
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

    private int nextMessageSortNo(WorkflowContext context) {
        return CollectionUtils.isEmpty(context.getSessionMessages()) ? 1 : context.getSessionMessages().size() + 1;
    }

    private int nextArtifactSeqNo(WorkflowContext context) {
        return CollectionUtils.isEmpty(context.getSessionArtifacts()) ? 1 : context.getSessionArtifacts().size() + 1;
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
