package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.service.AiChatArtifactService;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import ai.platform.aiassit.conversation.dto.protocol.RenderArtifactResponse;
import ai.platform.aiassit.conversation.dto.protocol.RoundThinkingResponse;
import ai.platform.aiassit.conversation.service.ConversationProtocolQueryService;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DefaultConversationProtocolQueryService implements ConversationProtocolQueryService {

    private final AiChatRoundService roundService;
    private final AiChatArtifactService artifactService;
    private final ObjectMapper objectMapper;

    public DefaultConversationProtocolQueryService(AiChatRoundService roundService,
                                                   AiChatArtifactService artifactService,
                                                   ObjectMapper objectMapper) {
        this.roundService = roundService;
        this.artifactService = artifactService;
        this.objectMapper = objectMapper;
    }

    @Override
    public RoundThinkingResponse thinkingDetail(String sessionCode, String roundCode, Long userId) {
        if (!StringUtils.hasText(sessionCode)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_SESSION_CODE);
        }
        if (!StringUtils.hasText(roundCode)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_ROUND_CODE);
        }

        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setRoundCode(roundCode);
        AiChatRoundDTO round = roundService.queryAll(query).stream()
                .filter(item -> item != null && Objects.equals(userId, item.getUserId()))
                .max(Comparator.comparing(AiChatRoundDTO::getId, Comparator.nullsLast(Long::compareTo)))
                .orElseThrow(() -> BizException.of(AiChatBizCodeConstant.CONVERSATION_ROUND_NOT_FOUND));

        List<AiChatArtifactDTO> artifacts = artifactService.queryAll(query).stream()
                .filter(item -> item != null && Objects.equals(userId, item.getUserId()))
                .sorted(Comparator.comparing(AiChatArtifactDTO::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        RoundThinkingResponse response = new RoundThinkingResponse();
        response.setSessionCode(sessionCode);
        response.setRoundCode(roundCode);
        response.setStatus(round.getStatus());
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (AiChatArtifactDTO artifact : artifacts) {
            String stage = StringUtils.hasText(artifact.getStage()) ? artifact.getStage() : "UNKNOWN";
            nodes.computeIfAbsent(stage, key -> node(key, artifact));
            response.getActivities().add(activity(artifact));
        }
        response.setNodes(List.copyOf(nodes.values()));
        response.getExt().put("activityCount", response.getActivities().size());
        return response;
    }

    @Override
    public RenderArtifactResponse renderArtifact(String codeRef, Long userId) {
        if (!StringUtils.hasText(codeRef)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_CONTENT);
        }
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setArtifactCode(codeRef);
        AiChatArtifactDTO artifact = artifactService.queryAll(query).stream()
                .filter(item -> item != null && Objects.equals(userId, item.getUserId()))
                .findFirst()
                .orElseThrow(() -> BizException.of(AiChatBizCodeConstant.CONVERSATION_NOT_FOUND));

        RenderArtifactResponse response = new RenderArtifactResponse();
        response.setCodeRef(artifact.getArtifactCode());
        response.setSessionCode(artifact.getSessionCode());
        response.setRoundCode(artifact.getRoundCode());
        response.setArtifactType(artifact.getArtifactType());
        response.setTitle(artifact.getTitle());
        response.setContentFormat(artifact.getContentFormat());
        response.setContent(parseContent(artifact.getContent(), artifact.getContentFormat()));
        response.setStatus(artifact.getStatus());
        response.getExt().putAll(parseMap(artifact.getExtJson()));
        return response;
    }

    private Map<String, Object> node(String stage, AiChatArtifactDTO artifact) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", stage.toLowerCase());
        node.put("title", stage);
        node.put("status", artifact.getStatus());
        return node;
    }

    private Map<String, Object> activity(AiChatArtifactDTO artifact) {
        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("id", artifact.getArtifactCode());
        activity.put("nodeId", artifact.getStage());
        activity.put("title", artifact.getTitle());
        activity.put("status", artifact.getStatus());
        activity.put("artifactType", artifact.getArtifactType());
        activity.put("contentFormat", artifact.getContentFormat());
        return activity;
    }

    private Object parseContent(String content, String contentFormat) {
        if (!StringUtils.hasText(content) || !"JSON".equalsIgnoreCase(contentFormat)) {
            return content;
        }
        try {
            return objectMapper.readValue(content, Object.class);
        } catch (JsonProcessingException ignored) {
            return content;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String content) {
        if (!StringUtils.hasText(content)) {
            return Map.of();
        }
        try {
            Object value = objectMapper.readValue(content, Object.class);
            return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of("value", value);
        } catch (JsonProcessingException ignored) {
            return Map.of("value", content);
        }
    }
}
