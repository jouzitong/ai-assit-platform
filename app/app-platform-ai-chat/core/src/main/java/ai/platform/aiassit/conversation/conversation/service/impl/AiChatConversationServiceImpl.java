package ai.platform.aiassit.conversation.conversation.service.impl;

import ai.platform.aiassit.conversation.conversation.service.AiChatConversationService;
import ai.platform.aiassit.conversation.query.dto.AiChatConversationDetailResponse;
import ai.platform.aiassit.conversation.query.dto.AiChatConversationRoundDetailVO;
import ai.platform.aiassit.conversation.query.dto.AiChatConversationPinRequest;
import ai.platform.aiassit.conversation.query.dto.AiChatConversationQueryRequest;
import ai.platform.aiassit.conversation.query.dto.AiChatConversationRenameRequest;
import ai.platform.aiassit.conversation.query.req.AiChatConversationCreateRequest;
import ai.platform.aiassit.conversation.query.req.AiChatConversationDeleteRequest;
import ai.platform.aiassit.conversation.query.req.AiChatConversationDetailRequest;
import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import ai.platform.aiassit.chat.history.service.AiChatArtifactService;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiChatConversationServiceImpl implements AiChatConversationService {

    private static final String DEFAULT_SESSION_NAME = "智能问数";

    private final AiChatSessionService sessionService;
    private final AiChatRoundService roundService;
    private final AiChatMessageService messageService;
    private final AiChatArtifactService artifactService;

    public AiChatConversationServiceImpl(AiChatSessionService sessionService,
                                         AiChatRoundService roundService,
                                         AiChatMessageService messageService,
                                         AiChatArtifactService artifactService) {
        this.sessionService = sessionService;
        this.roundService = roundService;
        this.messageService = messageService;
        this.artifactService = artifactService;
    }

    @Override
    public List<AiChatSessionDTO> listConversations(AiChatConversationQueryRequest request) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        if (request != null) {
            query.setCreatedBy(request.getUserId());
            query.setSessionCode(request.getSessionCode());
            query.setBusinessType(request.getBusinessType());
        }
        return sessionService.queryAll(query);
    }

    @Override
    public AiChatConversationDetailResponse detailConversation(AiChatConversationDetailRequest request) {
        if (request == null || !StringUtils.hasText(request.getSessionCode())) {
            throw new IllegalArgumentException("sessionCode is required");
        }

        AiChatConversationDetailResponse response = new AiChatConversationDetailResponse();
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(request.getSessionCode());
//        query.setCreatedBy(request.getUserId());

        response.setSession(sessionService.get(query));
        response.setRounds(buildRoundDetails(
                roundService.queryAll(query),
                messageService.queryAll(query),
                artifactService.queryAll(query)
        ));
        return response;
    }

    @Override
    public AiChatConversationDetailResponse createConversation(AiChatConversationCreateRequest request) {
        AiChatSessionDTO session = new AiChatSessionDTO();
        session.setSessionCode(generateCode("session"));
        session.setUserId(resolveUserId(request == null ? null : request.getUserId()));
        session.setBusinessType(request == null || request.getBusinessType() == null
                ? AiChatBusinessType.GENERAL
                : request.getBusinessType());
        session.setSessionName(resolveSessionName(request == null ? null : request.getSessionName()));
        AiChatSessionDTO created = sessionService.add(session);

        AiChatConversationDetailResponse response = new AiChatConversationDetailResponse();
        response.setSession(created);
        return response;
    }

    @Override
    public AiChatSessionDTO renameConversation(AiChatConversationRenameRequest request) {
        AiChatSessionDTO session = loadConversationSession(request == null ? null : request.getSessionCode(),
                request == null ? null : request.getUserId());
        AiChatSessionDTO update = new AiChatSessionDTO();
        update.setSessionName(resolveSessionName(request == null ? null : request.getSessionName()));
        return sessionService.edit(session.getId(), update);
    }

    @Override
    public AiChatSessionDTO pinConversation(AiChatConversationPinRequest request) {
        AiChatSessionDTO session = loadConversationSession(request == null ? null : request.getSessionCode(),
                request == null ? null : request.getUserId());
        AiChatSessionDTO update = new AiChatSessionDTO();
        update.setPinned(resolvePinned(request == null ? null : request.getPinned(), session.getPinned()));
        return sessionService.edit(session.getId(), update);
    }

    @Override
    public Boolean deleteConversation(AiChatConversationDeleteRequest request) {
        AiChatSessionDTO session = loadConversationSession(request == null ? null : request.getSessionCode(),
                request == null ? null : request.getUserId());
        deleteConversationHistory(session.getSessionCode(), session.getUserId());
        return sessionService.delete(session.getId());
    }

    private AiChatSessionDTO loadConversationSession(String sessionCode, Long userId) {
        if (!StringUtils.hasText(sessionCode)) {
            throw new IllegalArgumentException("sessionCode is required");
        }
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(resolveUserId(userId));
        AiChatSessionDTO session = sessionService.get(query);
        if (session == null) {
            throw new IllegalArgumentException("conversation not found");
        }
        return session;
    }

    private void deleteConversationHistory(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = buildHistoryQuery(sessionCode, userId);
        for (AiChatMessageDTO message : messageService.queryAll(query)) {
            if (message.getId() != null) {
                messageService.delete(message.getId());
            }
        }
        artifactService.queryAll(query).forEach(artifact -> {
            if (artifact.getId() != null) {
                artifactService.delete(artifact.getId());
            }
        });
        for (AiChatRoundDTO round : roundService.queryAll(query)) {
            if (round.getId() != null) {
                roundService.delete(round.getId());
            }
        }
    }

    private AiChatHistoryQueryRequest buildHistoryQuery(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        return query;
    }

    private Boolean resolvePinned(Boolean requestedPinned, Boolean currentPinned) {
        if (requestedPinned != null) {
            return requestedPinned;
        }
        return !Boolean.TRUE.equals(currentPinned);
    }

    private String resolveSessionName(String sessionName) {
        if (StringUtils.hasText(sessionName)) {
            return sessionName.trim();
        }
        return DEFAULT_SESSION_NAME;
    }

    private long resolveUserId(Long userId) {
        return userId == null ? 0L : userId;
    }

    private String generateCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private List<AiChatConversationRoundDetailVO> buildRoundDetails(List<AiChatRoundDTO> rounds,
                                                                    List<AiChatMessageDTO> messages,
                                                                    List<AiChatArtifactDTO> artifacts) {
        Map<String, AiChatConversationRoundDetailVO> detailMap = new LinkedHashMap<>();

        rounds.stream()
                .sorted(Comparator.comparing(AiChatRoundDTO::getId, Comparator.nullsLast(Long::compareTo)))
                .forEach(round -> {
                    AiChatConversationRoundDetailVO detail = new AiChatConversationRoundDetailVO();
                    detail.setRound(round);
                    detailMap.put(round.getRoundCode(), detail);
                });

        messages.stream()
                .sorted(Comparator.comparing(AiChatMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .forEach(message -> detailMap
                        .computeIfAbsent(message.getRoundCode(), key -> new AiChatConversationRoundDetailVO())
                        .getMessages()
                        .add(message));

        artifacts.stream()
                .sorted(Comparator.comparing(AiChatArtifactDTO::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .forEach(artifact -> detailMap
                        .computeIfAbsent(artifact.getRoundCode(), key -> new AiChatConversationRoundDetailVO())
                        .getArtifacts()
                        .add(artifact));

        return detailMap.values().stream()
                .peek(this::markPendingRenderType)
                .toList();
    }

    private void markPendingRenderType(AiChatConversationRoundDetailVO detail) {
        if (detail == null) {
            return;
        }
        boolean hasRenderableArtifact = detail.getArtifacts().stream()
                .anyMatch(artifact -> artifact != null
                        && StringUtils.hasText(artifact.getArtifactType())
                        && StringUtils.hasText(artifact.getContentFormat()));
        if (hasRenderableArtifact) {
            detail.setRenderType("TODO");
        }
    }
}
