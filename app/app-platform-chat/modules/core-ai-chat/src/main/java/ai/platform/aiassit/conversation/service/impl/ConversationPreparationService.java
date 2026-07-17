package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationArtifactDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.data.entity.req.ConversationHistoryQueryRequest;
import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
import ai.platform.aiassit.conversation.data.enums.ConversationRoundType;
import ai.platform.aiassit.conversation.data.service.ConversationArtifactService;
import ai.platform.aiassit.conversation.data.service.ConversationMessageService;
import ai.platform.aiassit.conversation.data.service.ConversationRoundService;
import ai.platform.aiassit.conversation.data.service.ConversationSessionService;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.workflow.support.AgentConversationHistoryRecorder;
import ai.platform.aiassit.conversation.data.enums.ConversationActorType;
import ai.platform.aiassit.conversation.data.enums.ConversationContentFormat;
import ai.platform.aiassit.conversation.data.enums.ConversationDisplayLevel;
import ai.platform.aiassit.conversation.data.enums.ConversationMessageType;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import org.apache.commons.collections4.CollectionUtils;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.security.auth.core.context.SecurityContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ConversationPreparationService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_RUNNING = "RUNNING";

    private final ConversationSessionService sessionService;
    private final ConversationMessageService messageService;
    private final ConversationArtifactService artifactService;
    private final ConversationRoundService roundService;
    private final AgentConversationHistoryRecorder historyRecorder;

    public ConversationPreparationService(ConversationSessionService sessionService,
                                                ConversationMessageService messageService,
                                                ConversationArtifactService artifactService,
                                                ConversationRoundService roundService,
                                                AgentConversationHistoryRecorder historyRecorder) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.artifactService = artifactService;
        this.roundService = roundService;
        this.historyRecorder = historyRecorder;
    }

    public void prepare(ConversationRuntimeContext context) {
        log.info("开始准备对话流上下文，context={}", context);
        prepareConversationRuntimeContext(context);
        log.info("会话与轮次准备完成，context={}", context);
    }

    private void prepareConversationRuntimeContext(ConversationRuntimeContext context) {
        ConversationQueryCommand command = context.getCommand();
        if (command == null) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_QUERY_COMMAND);
        }
        if (!org.springframework.util.StringUtils.hasText(command.getMessage())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_MESSAGE);
        }

        String sessionCode = command.getSessionCode();
        Long userId = resolveUserId(command.getUserId());

        ConversationSessionDTO session;
        List<ConversationMessageDTO> sessionMessages;
        List<ConversationArtifactDTO> sessionArtifacts;
        if (!org.springframework.util.StringUtils.hasText(sessionCode)) {
            session = createSession(command, userId);
            sessionMessages = List.of();
            sessionArtifacts = List.of();
            command.setSessionCode(session.getSessionCode());
        } else {
            session = loadSession(sessionCode, userId);
            if (session == null) {
                throw BizException.of(AiChatBizCodeConstant.CONVERSATION_NOT_FOUND, sessionCode);
            }
            validateSessionBusinessType(session, command);
            sessionMessages = loadSessionMessages(sessionCode, userId);
            sessionArtifacts = loadSessionArtifacts(sessionCode, userId);
        }

        context.setSession(session);
        context.setSessionArtifacts(sessionArtifacts);
        context.getOrCreateUserMessageContext().setSessionMessages(sessionMessages);

        ConversationRoundDTO round = createRound(session, command, userId);
        context.setRound(round);

        ConversationMessageDTO lastMessage = sessionMessages.isEmpty() ? null : sessionMessages.get(sessionMessages.size() - 1);
        ConversationMessageDTO userMessage = historyRecorder.saveMessage(
                context,
                round.getRoundCode(),
                "USER",
                ConversationActorType.HUMAN.name(),
                ConversationMessageType.USER_INPUT.name(),
                command.getMessage(),
                ConversationContentFormat.PLAIN_TEXT.name(),
                ConversationDisplayLevel.VISIBLE.name(),
                STATUS_SUCCESS,
                lastMessage == null ? null : lastMessage.getMessageCode(),
                lastMessage == null ? null : lastMessage.getMessageCode(),
                null
        );
        context.getOrCreateUserMessageContext().setCurrentMessage(userMessage);
        context.refreshUserMessageContext();
        log.info("用户消息已写入当前对话轮次，context={}", context);
    }

    private ConversationSessionDTO createSession(ConversationQueryCommand command, Long userId) {
        ConversationSessionDTO session = new ConversationSessionDTO();
        session.setSessionCode(generateCode("session"));
        session.setUserId(userId);
        session.setBusinessType(resolveBusinessType(command.getBusinessType()));
        session.setSessionName(resolveSessionName(command));
        session.setPinned(Boolean.FALSE);
        return sessionService.add(session);
    }

    private ConversationSessionDTO loadSession(String sessionCode, Long userId) {
        ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setUserId(userId);
        return sessionService.get(query);
    }

    private List<ConversationMessageDTO> loadSessionMessages(String sessionCode, Long userId) {
        ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setUserId(userId);
        return messageService.queryAll(query).stream()
                .sorted(Comparator.comparing(ConversationMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<ConversationArtifactDTO> loadSessionArtifacts(String sessionCode, Long userId) {
        ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setUserId(userId);
        return artifactService.queryAll(query);
    }

    private ConversationRoundDTO createRound(ConversationSessionDTO session, ConversationQueryCommand command, Long userId) {
        ConversationRoundDTO round = new ConversationRoundDTO();
        round.setRoundCode(org.springframework.util.StringUtils.hasText(command.getRoundCode())
                ? command.getRoundCode().trim()
                : generateCode("round"));
        round.setRoundType(resolveRoundType(command));
        round.setParentRoundCode(resolveParentRoundCode(session.getSessionCode(), userId));
        round.setSessionCode(session.getSessionCode());
        round.setUserId(userId);
        round.setModelCode(resolveModelCode(command.getApiModel()));
        round.setActualModel(resolveActualModel(command));
        round.setStatus(STATUS_RUNNING);
        return roundService.add(round);
    }

    private Long resolveUserId(Long userId) {
        if (SecurityContextHolder.get() != null && SecurityContextHolder.get().subject() != null) {
            return SecurityContextHolder.get().subject().userId();
        }
        return userId == null ? 0L : userId;
    }

    private ConversationBusinessType resolveBusinessType(ConversationBusinessType businessType) {
        return businessType == null ? ConversationBusinessType.CUSTOM : businessType;
    }

    void validateSessionBusinessType(ConversationSessionDTO session, ConversationQueryCommand command) {
        ConversationBusinessType expected = resolveBusinessType(command.getBusinessType());
        ConversationBusinessType persisted = session.getBusinessType();
        boolean matches = expected == ConversationBusinessType.PAGE_ASSISTANT
                ? persisted == ConversationBusinessType.PAGE_ASSISTANT
                : persisted == null
                || persisted == ConversationBusinessType.GENERAL
                || persisted == ConversationBusinessType.CUSTOM;
        if (matches) {
            return;
        }
        log.warn("会话业务类型不匹配，sessionCode={}, persistedBusinessType={}, requestedBusinessType={}",
                session.getSessionCode(), persisted, expected);
        // Return the same public error as an inaccessible session to avoid leaking cross-channel state.
        throw BizException.of(AiChatBizCodeConstant.CONVERSATION_NOT_FOUND, session.getSessionCode());
    }

    private String resolveSessionName(ConversationQueryCommand command) {
        if (org.springframework.util.StringUtils.hasText(command.getSessionName())) {
            return command.getSessionName().trim();
        }
        if (!org.springframework.util.StringUtils.hasText(command.getMessage())) {
            return "新会话";
        }
        String content = command.getMessage().trim();
        return content.length() > 20 ? content.substring(0, 20) : content;
    }

    private ConversationRoundType resolveRoundType(ConversationQueryCommand command) {
        String explicitRoundType = readExtText(command, "roundType");
        if (explicitRoundType != null) {
            return ConversationRoundType.fromName(explicitRoundType);
        }
        return ConversationRoundType.AGENT_CHAT;
    }

    private String readExtText(ConversationQueryCommand command, String key) {
        Object value = command == null || command.getExt() == null ? null : command.getExt().get(key);
        if (value instanceof String str && org.springframework.util.StringUtils.hasText(str)) {
            return str.trim();
        }
        return null;
    }

    private String resolveParentRoundCode(String sessionCode, Long userId) {
        ConversationHistoryQueryRequest query = new ConversationHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setUserId(userId);
        List<ConversationRoundDTO> rounds = roundService.queryAll(query);
        if (CollectionUtils.isEmpty(rounds)) {
            return null;
        }
        return rounds.get(rounds.size() - 1).getRoundCode();
    }

    private String resolveModelCode(String apiModel) {
        return org.springframework.util.StringUtils.hasText(apiModel) ? apiModel.trim() : "DEFAULT";
    }

    private String resolveActualModel(ConversationQueryCommand command) {
        if (command != null && org.springframework.util.StringUtils.hasText(command.getActualModel())) {
            return command.getActualModel().trim();
        }
        return command != null && org.springframework.util.StringUtils.hasText(command.getApiModel())
                ? command.getApiModel().trim()
                : "DEFAULT";
    }

    private String generateCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}
