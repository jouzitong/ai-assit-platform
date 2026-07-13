package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatActivityDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.service.AiChatArtifactService;
import ai.platform.aiassit.chat.history.service.AiChatActivityService;
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
    private final AiChatActivityService activityService;
    private final ObjectMapper objectMapper;

    public DefaultConversationProtocolQueryService(AiChatRoundService roundService,
                                                   AiChatArtifactService artifactService,
                                                   AiChatActivityService activityService,
                                                   ObjectMapper objectMapper) {
        this.roundService = roundService;
        this.artifactService = artifactService;
        this.activityService = activityService;
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
        List<AiChatActivityDTO> activities = activityService.queryAll(query).stream()
                .filter(item -> item != null && Objects.equals(userId, item.getUserId()))
                .sorted(Comparator.comparing(AiChatActivityDTO::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        RoundThinkingResponse response = new RoundThinkingResponse();
        response.setSessionCode(sessionCode);
        response.setRoundCode(roundCode);
        response.setStatus(round.getStatus());
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (AiChatArtifactDTO artifact : artifacts) {
            String stage = StringUtils.hasText(artifact.getStage()) ? artifact.getStage() : "UNKNOWN";
            nodes.computeIfAbsent(stage, key -> node(key, artifact));
        }
        for (AiChatActivityDTO activity : activities) {
            String nodeCode = StringUtils.hasText(activity.getNodeCode()) ? activity.getNodeCode() : "AI_AGENT";
            nodes.put(nodeCode, node(nodeCode, activity, nodes.get(nodeCode)));
            response.getActivities().add(activity(activity));
        }
        response.setNodes(List.copyOf(nodes.values()));
        response.getExt().put("activityCount", response.getActivities().size());
        response.getExt().put("artifactCount", artifacts.size());
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

    private Map<String, Object> node(String nodeCode,
                                     AiChatActivityDTO activity,
                                     Map<String, Object> current) {
        Map<String, Object> node = current == null ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
        node.put("id", nodeCode);
        node.putIfAbsent("title", nodeCode);
        node.put("status", activity.getStatus());
        if (StringUtils.hasText(activity.getMessage())) {
            node.put("description", activity.getMessage());
        }
        return node;
    }

    private Map<String, Object> activity(AiChatActivityDTO record) {
        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("id", record.getActivityCode());
        activity.put("activityCode", record.getCorrelationCode());
        activity.put("nodeId", record.getNodeCode());
        activity.put("activityType", record.getActivityType());
        activity.put("title", record.getActivityName());
        activity.put("status", record.getStatus());
        activity.put("source", record.getSource());
        activity.put("phase", record.getPhase());
        activity.put("description", record.getMessage());
        activity.put("inputSummary", record.getInputSummary());
        activity.put("outputSummary", record.getOutputSummary());
        activity.put("durationMs", record.getDurationMs());
        activity.put("seqNo", record.getSeqNo());
        Map<String, Object> detail = parseMap(record.getDetailJson());
        if (!detail.isEmpty()) {
            activity.put("detail", detail);
        }
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
