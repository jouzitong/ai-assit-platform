package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationActivityDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationArtifactDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import ai.platform.aiassit.conversation.data.entity.req.ConversationHistoryQueryRequest;
import ai.platform.aiassit.conversation.data.service.ConversationActivityService;
import ai.platform.aiassit.conversation.data.service.ConversationArtifactService;
import ai.platform.aiassit.conversation.data.service.ConversationMessageService;
import ai.platform.aiassit.conversation.data.service.ConversationRoundService;
import ai.platform.aiassit.conversation.data.service.ConversationSessionService;
import ai.platform.aiassit.conversation.dto.conversation.ConversationHistoryPageResponse;
import ai.platform.aiassit.conversation.dto.conversation.ConversationHistoryWindowResponse;
import ai.platform.aiassit.conversation.dto.conversation.ConversationRoundDetailVO;
import ai.platform.aiassit.conversation.service.ConversationHistoryService;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Bounded history orchestration; all reads remain scoped by the authenticated session owner. */
@Service
public class ConversationHistoryServiceImpl implements ConversationHistoryService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int WINDOW_SIDE_SIZE = 10;

    private final ConversationSessionService sessionService;
    private final ConversationRoundService roundService;
    private final ConversationMessageService messageService;
    private final ConversationArtifactService artifactService;
    private final ConversationActivityService activityService;
    private final ConversationHistoryCursorCodec cursorCodec;

    public ConversationHistoryServiceImpl(ConversationSessionService sessionService,
                                           ConversationRoundService roundService,
                                           ConversationMessageService messageService,
                                           ConversationArtifactService artifactService,
                                           ConversationActivityService activityService,
                                           ConversationHistoryCursorCodec cursorCodec) {
        this.sessionService = sessionService;
        this.roundService = roundService;
        this.messageService = messageService;
        this.artifactService = artifactService;
        this.activityService = activityService;
        this.cursorCodec = cursorCodec;
    }

    @Override
    public ConversationHistoryPageResponse page(String tenantId,
                                                Long userId,
                                                String sessionCode,
                                                String beforeCursor,
                                                int limit) {
        String normalizedSessionCode = ownedSession(tenantId, userId, sessionCode);
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        Long beforeId;
        try {
            beforeId = cursorCodec.decode(beforeCursor, normalizedSessionCode, userId);
        } catch (IllegalArgumentException ex) {
            throw BizException.illegalParam(AiChatBizCodeConstant.INVALID_HISTORY_CURSOR);
        }
        List<ConversationRoundDTO> candidates = beforeId == null
                ? roundService.queryRecent(normalizedSessionCode, userId, safeLimit + 1)
                : roundService.queryBefore(normalizedSessionCode, userId, beforeId, safeLimit + 1);
        boolean hasMore = candidates.size() > safeLimit;
        List<ConversationRoundDTO> selected = hasMore
                ? new ArrayList<>(candidates.subList(1, candidates.size()))
                : new ArrayList<>(candidates);

        ConversationHistoryPageResponse response = new ConversationHistoryPageResponse();
        response.setSessionCode(normalizedSessionCode);
        response.setRounds(assemble(normalizedSessionCode, userId, selected));
        response.setHasMore(hasMore && !selected.isEmpty() && selected.get(0).getId() != null);
        if (response.isHasMore()) {
            response.setNextCursor(cursorCodec.encode(
                    normalizedSessionCode, userId, selected.get(0).getId()));
        }
        return response;
    }

    @Override
    public ConversationHistoryWindowResponse window(String tenantId,
                                                    Long userId,
                                                    String sessionCode,
                                                    String aroundRoundCode) {
        String normalizedSessionCode = ownedSession(tenantId, userId, sessionCode);
        if (!StringUtils.hasText(aroundRoundCode)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_ROUND_CODE);
        }
        ConversationRoundDTO center = roundService.queryOwned(
                aroundRoundCode.trim(), normalizedSessionCode, userId);
        if (center == null) {
            throw BizException.of(AiChatBizCodeConstant.CONVERSATION_ROUND_NOT_FOUND);
        }

        List<ConversationRoundDTO> before = new ArrayList<>();
        List<ConversationRoundDTO> after = new ArrayList<>();
        boolean hasEarlier = false;
        boolean hasLater = false;
        if (center.getId() != null) {
            List<ConversationRoundDTO> beforeCandidates = roundService.queryBefore(
                    normalizedSessionCode, userId, center.getId(), WINDOW_SIDE_SIZE + 1);
            hasEarlier = beforeCandidates.size() > WINDOW_SIDE_SIZE;
            before.addAll(hasEarlier
                    ? beforeCandidates.subList(1, beforeCandidates.size()) : beforeCandidates);

            List<ConversationRoundDTO> afterCandidates = roundService.queryAfter(
                    normalizedSessionCode, userId, center.getId(), WINDOW_SIDE_SIZE + 1);
            hasLater = afterCandidates.size() > WINDOW_SIDE_SIZE;
            after.addAll(hasLater
                    ? afterCandidates.subList(0, WINDOW_SIDE_SIZE) : afterCandidates);
        }

        List<ConversationRoundDTO> rounds = new ArrayList<>(before);
        rounds.add(center);
        rounds.addAll(after);
        rounds.sort(Comparator.comparing(ConversationRoundDTO::getId,
                Comparator.nullsLast(Long::compareTo)));

        ConversationHistoryWindowResponse response = new ConversationHistoryWindowResponse();
        response.setSessionCode(normalizedSessionCode);
        response.setAroundRoundCode(center.getRoundCode());
        response.setRounds(assemble(normalizedSessionCode, userId, rounds));
        response.setHasEarlier(hasEarlier && !before.isEmpty() && before.get(0).getId() != null);
        response.setHasLater(hasLater && !after.isEmpty() && after.get(after.size() - 1).getId() != null);
        if (response.isHasEarlier()) {
            response.setBeforeCursor(cursorCodec.encode(
                    normalizedSessionCode, userId, before.get(0).getId()));
        }
        if (response.isHasLater()) {
            response.setAfterCursor(cursorCodec.encode(
                    normalizedSessionCode, userId, after.get(after.size() - 1).getId()));
        }
        return response;
    }

    private String ownedSession(String tenantId, Long userId, String sessionCode) {
        if (!StringUtils.hasText(tenantId) || userId == null || !StringUtils.hasText(sessionCode)) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_SESSION_CODE);
        }
        String normalized = sessionCode.trim();
        ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
        query.setSessionCode(normalized);
        query.setUserId(userId);
        if (sessionService.get(query) == null) {
            throw BizException.of(AiChatBizCodeConstant.CONVERSATION_NOT_FOUND, normalized);
        }
        return normalized;
    }

    private List<ConversationRoundDetailVO> assemble(String sessionCode,
                                                      Long userId,
                                                      List<ConversationRoundDTO> rounds) {
        if (rounds == null || rounds.isEmpty()) {
            return List.of();
        }
        List<String> roundCodes = rounds.stream()
                .map(ConversationRoundDTO::getRoundCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<ConversationMessageDTO> messages = safeMessages(roundCodes);
        List<ConversationArtifactDTO> artifacts = safeArtifacts(roundCodes);
        List<ConversationActivityDTO> activities = safeActivities(sessionCode, userId, roundCodes);
        Set<String> codeSet = roundCodes.stream().collect(Collectors.toSet());

        Map<String, ConversationRoundDetailVO> details = new LinkedHashMap<>();
        rounds.forEach(round -> {
            if (round == null || !StringUtils.hasText(round.getRoundCode())) {
                return;
            }
            ConversationRoundDetailVO detail = new ConversationRoundDetailVO();
            detail.setRound(round);
            details.put(round.getRoundCode(), detail);
        });
        messages.stream().filter(message -> message != null && codeSet.contains(message.getRoundCode()))
                .sorted(Comparator.comparing(ConversationMessageDTO::getSortNo,
                        Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ConversationMessageDTO::getId,
                                Comparator.nullsLast(Long::compareTo)))
                .forEach(message -> details.get(message.getRoundCode()).getMessages().add(message));
        artifacts.stream().filter(artifact -> artifact != null && codeSet.contains(artifact.getRoundCode()))
                .sorted(Comparator.comparing(ConversationArtifactDTO::getSeqNo,
                        Comparator.nullsLast(Integer::compareTo)))
                .forEach(artifact -> details.get(artifact.getRoundCode()).getArtifacts().add(artifact));
        activities.stream().filter(activity -> activity != null && codeSet.contains(activity.getRoundCode()))
                .sorted(Comparator.comparing(ConversationActivityDTO::getSeqNo,
                        Comparator.nullsLast(Integer::compareTo)))
                .forEach(activity -> details.get(activity.getRoundCode()).getActivities().add(activity));
        details.values().forEach(this::markPendingRenderType);
        return new ArrayList<>(details.values());
    }

    private List<ConversationMessageDTO> safeMessages(List<String> roundCodes) {
        List<ConversationMessageDTO> result = messageService.queryByRoundCodes(roundCodes);
        return result == null ? List.of() : result;
    }

    private List<ConversationArtifactDTO> safeArtifacts(List<String> roundCodes) {
        List<ConversationArtifactDTO> result = artifactService.queryByRoundCodes(roundCodes);
        return result == null ? List.of() : result;
    }

    private List<ConversationActivityDTO> safeActivities(String sessionCode,
                                                         Long userId,
                                                         List<String> roundCodes) {
        List<ConversationActivityDTO> result = activityService.queryByRoundCodes(
                sessionCode, userId, roundCodes);
        return result == null ? List.of() : result;
    }

    private void markPendingRenderType(ConversationRoundDetailVO detail) {
        boolean hasRenderableArtifact = detail.getArtifacts().stream()
                .anyMatch(artifact -> artifact != null && artifact.getArtifactType() != null
                        && "RENDER_JSON".equalsIgnoreCase(artifact.getArtifactType()));
        if (hasRenderableArtifact) {
            detail.setRenderType("TODO");
        }
    }
}
