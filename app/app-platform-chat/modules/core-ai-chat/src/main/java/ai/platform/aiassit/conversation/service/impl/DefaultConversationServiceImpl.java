package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.conversation.service.ConversationService;
import ai.platform.aiassit.conversation.dto.conversation.ConversationDetailResponse;
import ai.platform.aiassit.conversation.dto.conversation.ConversationRoundDetailVO;
import ai.platform.aiassit.conversation.dto.conversation.ConversationPinRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationQueryRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationRenameRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationCreateRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationDeleteRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationDetailRequest;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationArtifactDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationActivityDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.data.entity.req.ConversationHistoryQueryRequest;
import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
import ai.platform.aiassit.conversation.data.service.ConversationArtifactService;
import ai.platform.aiassit.conversation.data.service.ConversationActivityService;
import ai.platform.aiassit.conversation.data.service.ConversationMessageService;
import ai.platform.aiassit.conversation.data.service.ConversationRoundService;
import ai.platform.aiassit.conversation.data.service.ConversationSessionService;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DefaultConversationServiceImpl implements ConversationService {

    private static final String DEFAULT_SESSION_NAME = "智能问数";

    private final ConversationSessionService sessionService;
    private final ConversationRoundService roundService;
    private final ConversationMessageService messageService;
    private final ConversationArtifactService artifactService;
    private final ConversationActivityService activityService;

    public DefaultConversationServiceImpl(ConversationSessionService sessionService,
                                         ConversationRoundService roundService,
                                         ConversationMessageService messageService,
                                         ConversationArtifactService artifactService,
                                         ConversationActivityService activityService) {
        this.sessionService = sessionService;
        this.roundService = roundService;
        this.messageService = messageService;
        this.artifactService = artifactService;
        this.activityService = activityService;
    }

    @Override
    public List<ConversationSessionDTO> listConversations(ConversationQueryRequest request) {
        ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
        if (request != null) {
            query.setUserId(request.getUserId());
            query.setSessionCode(request.getSessionCode());
            query.setGroupCode(request.getGroupCode());
            query.setBusinessType(request.getBusinessType());
        }
        List<ConversationSessionDTO> sessions = sessionService.queryAll(query);
        if (request != null && request.getBusinessType() != null) {
            return sessions;
        }
        return sessions.stream()
                .filter(session -> session.getBusinessType() != ConversationBusinessType.PAGE_ASSISTANT)
                .toList();
    }

    @Override
    public ConversationDetailResponse detailConversation(ConversationDetailRequest request) {
        if (request == null || !StringUtils.hasText(request.getSessionCode())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_SESSION_CODE);
        }

        ConversationDetailResponse response = new ConversationDetailResponse();
        ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
        query.setSessionCode(request.getSessionCode());
        query.setUserId(request.getUserId());

        ConversationSessionDTO session = sessionService.get(query);
        if (session == null) {
            throw BizException.of(AiChatBizCodeConstant.CONVERSATION_NOT_FOUND);
        }
        List<ConversationRoundDTO> rounds = roundService.queryAll(query);
        response.setSession(session);
        response.setRounds(buildRoundDetails(
                rounds,
                messageService.queryAll(query),
                artifactService.queryByRoundCodes(roundCodes(rounds)),
                activityService.queryAll(query)
        ));
        return response;
    }

    @Override
    public ConversationDetailResponse createConversation(ConversationCreateRequest request) {
        ConversationSessionDTO session = new ConversationSessionDTO();
        session.setSessionCode(generateCode("session"));
        session.setUserId(resolveUserId(request == null ? null : request.getUserId()));
        session.setBusinessType(request == null || request.getBusinessType() == null
                ? ConversationBusinessType.GENERAL
                : request.getBusinessType());
        session.setSessionName(resolveSessionName(request == null ? null : request.getSessionName()));
        ConversationSessionDTO created = sessionService.add(session);

        ConversationDetailResponse response = new ConversationDetailResponse();
        response.setSession(created);
        return response;
    }

    @Override
    public ConversationSessionDTO renameConversation(ConversationRenameRequest request) {
        ConversationSessionDTO session = loadConversationSession(request == null ? null : request.getSessionCode(),
                request == null ? null : request.getUserId());
        ConversationSessionDTO update = new ConversationSessionDTO();
        update.setSessionName(resolveSessionName(request == null ? null : request.getSessionName()));
        return sessionService.edit(session.getId(), update);
    }

    @Override
    public ConversationSessionDTO pinConversation(ConversationPinRequest request) {
        ConversationSessionDTO session = loadConversationSession(request == null ? null : request.getSessionCode(),
                request == null ? null : request.getUserId());
        ConversationSessionDTO update = new ConversationSessionDTO();
        update.setPinned(resolvePinned(request == null ? null : request.getPinned(), session.getPinned()));
        return sessionService.edit(session.getId(), update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteConversation(ConversationDeleteRequest request) {
        ConversationSessionDTO session = loadConversationSession(request == null ? null : request.getSessionCode(),
                request == null ? null : request.getUserId());
        deleteConversationHistory(session.getSessionCode(), session.getUserId());
        return sessionService.delete(session.getId());
    }

    private ConversationSessionDTO loadConversationSession(String sessionCode, Long userId) {
        if (!StringUtils.hasText(sessionCode)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_SESSION_CODE);
        }
        ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setUserId(userId);
        ConversationSessionDTO session = sessionService.get(query);
        if (session == null) {
            throw BizException.of(AiChatBizCodeConstant.CONVERSATION_NOT_FOUND);
        }
        return session;
    }

    private void deleteConversationHistory(String sessionCode, Long userId) {
        ConversationHistoryQueryRequest query = buildHistoryQuery(sessionCode, userId);
        List<ConversationRoundDTO> rounds = roundService.queryAll(query);
        for (ConversationMessageDTO message : messageService.queryAll(query)) {
            if (message.getId() != null) {
                messageService.delete(message.getId());
            }
        }
        artifactService.queryByRoundCodes(roundCodes(rounds)).forEach(artifact -> {
            if (artifact.getId() != null) {
                artifactService.delete(artifact.getId());
            }
        });
        activityService.queryAll(query).forEach(activity -> {
            if (activity.getId() != null) {
                activityService.delete(activity.getId());
            }
        });
        for (ConversationRoundDTO round : rounds) {
            if (round.getId() != null) {
                roundService.delete(round.getId());
            }
        }
    }

    private ConversationHistoryQueryRequest buildHistoryQuery(String sessionCode, Long userId) {
        ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setUserId(userId);
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

    private List<String> roundCodes(List<ConversationRoundDTO> rounds) {
        if (rounds == null) {
            return List.of();
        }
        return rounds.stream()
                .map(ConversationRoundDTO::getRoundCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String generateCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private List<ConversationRoundDetailVO> buildRoundDetails(List<ConversationRoundDTO> rounds,
                                                                    List<ConversationMessageDTO> messages,
                                                                    List<ConversationArtifactDTO> artifacts,
                                                                    List<ConversationActivityDTO> activities) {
        Map<String, ConversationRoundDetailVO> detailMap = new LinkedHashMap<>();

        rounds.stream()
                .sorted(Comparator.comparing(ConversationRoundDTO::getId, Comparator.nullsLast(Long::compareTo)))
                .forEach(round -> {
                    ConversationRoundDetailVO detail = new ConversationRoundDetailVO();
                    detail.setRound(round);
                    detailMap.put(round.getRoundCode(), detail);
                });

        messages.stream()
                .sorted(Comparator.comparing(ConversationMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .forEach(message -> detailMap
                        .computeIfAbsent(message.getRoundCode(), key -> new ConversationRoundDetailVO())
                        .getMessages()
                        .add(message));

        artifacts.stream()
                .sorted(Comparator.comparing(ConversationArtifactDTO::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .forEach(artifact -> detailMap
                        .computeIfAbsent(artifact.getRoundCode(), key -> new ConversationRoundDetailVO())
                        .getArtifacts()
                        .add(artifact));

        activities.stream()
                .sorted(Comparator.comparing(ConversationActivityDTO::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .forEach(activity -> detailMap
                        .computeIfAbsent(activity.getRoundCode(), key -> new ConversationRoundDetailVO())
                        .getActivities()
                        .add(activity));

        return detailMap.values().stream()
                .peek(this::markPendingRenderType)
                .toList();
    }

    private void markPendingRenderType(ConversationRoundDetailVO detail) {
        if (detail == null) {
            return;
        }
        boolean hasRenderableArtifact = detail.getArtifacts().stream()
                .anyMatch(artifact -> artifact != null
                        && "RENDER_JSON".equalsIgnoreCase(artifact.getArtifactType()));
        if (hasRenderableArtifact) {
            detail.setRenderType("TODO");
        }
    }
}
