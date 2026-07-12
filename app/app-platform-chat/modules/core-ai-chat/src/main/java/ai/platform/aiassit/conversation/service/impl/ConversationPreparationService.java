package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import ai.platform.aiassit.chat.history.enums.AiChatRoundType;
import ai.platform.aiassit.chat.history.service.AiChatArtifactService;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.conversation.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.workflow.support.WorkflowHistoryRecorder;
import ai.platform.aiassit.chat.history.enums.AiChatActorType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import ai.platform.aiassit.chat.history.enums.AiChatDisplayLevel;
import ai.platform.aiassit.chat.history.enums.AiChatMessageType;
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

    private final AiChatSessionService sessionService;
    private final AiChatMessageService messageService;
    private final AiChatArtifactService artifactService;
    private final AiChatRoundService roundService;
    private final WorkflowHistoryRecorder historyRecorder;
    private final ConversationIntentRouteService intentRouteService;

    public ConversationPreparationService(AiChatSessionService sessionService,
                                                AiChatMessageService messageService,
                                                AiChatArtifactService artifactService,
                                                AiChatRoundService roundService,
                                                WorkflowHistoryRecorder historyRecorder,
                                                ConversationIntentRouteService intentRouteService) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.artifactService = artifactService;
        this.roundService = roundService;
        this.historyRecorder = historyRecorder;
        this.intentRouteService = intentRouteService;
    }

    public void prepare(ConversationRuntimeContext context) {
        log.info("开始准备对话流上下文，context={}", context);
        prepareConversationRuntimeContext(context);
        log.info("会话与轮次准备完成，context={}", context);
        intentRouteService.route(context);
        log.info("对话流上下文路由完成，context={}", context);
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

        AiChatSessionDTO session;
        List<AiChatMessageDTO> sessionMessages;
        List<AiChatArtifactDTO> sessionArtifacts;
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
            sessionMessages = loadSessionMessages(sessionCode, userId);
            sessionArtifacts = loadSessionArtifacts(sessionCode, userId);
        }

        context.setSession(session);
        context.setSessionArtifacts(sessionArtifacts);
        context.getOrCreateUserMessageContext().setSessionMessages(sessionMessages);

        AiChatRoundDTO round = createRound(session, command, userId);
        context.setRound(round);

        AiChatMessageDTO lastMessage = sessionMessages.isEmpty() ? null : sessionMessages.get(sessionMessages.size() - 1);
        AiChatMessageDTO userMessage = historyRecorder.saveMessage(
                context,
                round.getRoundCode(),
                "USER",
                AiChatActorType.HUMAN.name(),
                AiChatMessageType.USER_INPUT.name(),
                command.getMessage(),
                AiChatContentFormat.PLAIN_TEXT.name(),
                AiChatDisplayLevel.VISIBLE.name(),
                STATUS_SUCCESS,
                lastMessage == null ? null : lastMessage.getMessageCode(),
                lastMessage == null ? null : lastMessage.getMessageCode(),
                null
        );
        context.getOrCreateUserMessageContext().setCurrentMessage(userMessage);
        context.refreshUserMessageContext();
        context.getOrCreateNodeResult(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode()).setStatus(STATUS_SUCCESS);
        context.putNodeOutput(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode(), "session", session);
        context.putNodeOutput(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode(), "currentMessage", userMessage);
        log.info("用户消息已写入当前对话轮次，context={}", context);
    }

    private AiChatSessionDTO createSession(ConversationQueryCommand command, Long userId) {
        AiChatSessionDTO session = new AiChatSessionDTO();
        session.setSessionCode(generateCode("session"));
        session.setUserId(userId);
        session.setBusinessType(resolveBusinessType(command.getBusinessType()));
        session.setSessionName(resolveSessionName(command));
        session.setPinned(Boolean.FALSE);
        return sessionService.add(session);
    }

    private AiChatSessionDTO loadSession(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setUserId(userId);
        return sessionService.get(query);
    }

    private List<AiChatMessageDTO> loadSessionMessages(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        return messageService.queryAll(query).stream()
                .sorted(Comparator.comparing(AiChatMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<AiChatArtifactDTO> loadSessionArtifacts(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setUserId(userId);
        return artifactService.queryAll(query);
    }

    private AiChatRoundDTO createRound(AiChatSessionDTO session, ConversationQueryCommand command, Long userId) {
        AiChatRoundDTO round = new AiChatRoundDTO();
        round.setRoundCode(org.springframework.util.StringUtils.hasText(command.getRoundCode())
                ? command.getRoundCode().trim()
                : generateCode("round"));
        round.setRoundType(resolveRoundType(command));
        round.setParentRoundCode(resolveParentRoundCode(session.getSessionCode(), userId));
        round.setSessionCode(session.getSessionCode());
        round.setUserId(userId);
        round.setModelCode(resolveModelCode(command.getApiModel()));
        round.setActualModel(resolveActualModel(command.getApiModel()));
        round.setStatus(STATUS_RUNNING);
        return roundService.add(round);
    }

    private Long resolveUserId(Long userId) {
        if (SecurityContextHolder.get() != null && SecurityContextHolder.get().subject() != null) {
            return SecurityContextHolder.get().subject().userId();
        }
        return userId == null ? 0L : userId;
    }

    private AiChatBusinessType resolveBusinessType(AiChatBusinessType businessType) {
        return businessType == null ? AiChatBusinessType.CUSTOM : businessType;
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

    private AiChatRoundType resolveRoundType(ConversationQueryCommand command) {
        String explicitRoundType = readExtText(command, "roundType");
        if (explicitRoundType != null) {
            return AiChatRoundType.fromIntentType(explicitRoundType);
        }
        return AiChatRoundType.QUERY_RENDER;
    }

    private String readExtText(ConversationQueryCommand command, String key) {
        Object value = command == null || command.getExt() == null ? null : command.getExt().get(key);
        if (value instanceof String str && org.springframework.util.StringUtils.hasText(str)) {
            return str.trim();
        }
        return null;
    }

    private String resolveParentRoundCode(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setUserId(userId);
        List<AiChatRoundDTO> rounds = roundService.queryAll(query);
        if (CollectionUtils.isEmpty(rounds)) {
            return null;
        }
        return rounds.get(rounds.size() - 1).getRoundCode();
    }

    private String resolveModelCode(String apiModel) {
        return org.springframework.util.StringUtils.hasText(apiModel) ? apiModel.trim() : "DEFAULT";
    }

    private String resolveActualModel(String apiModel) {
        return org.springframework.util.StringUtils.hasText(apiModel) ? apiModel.trim() : "DEFAULT";
    }

    private String generateCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}
