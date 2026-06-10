package ai.platform.aiassit.chat.core.workflow.node.impl;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.node.BaseWorkflowNode;
import ai.platform.aiassit.chat.core.workflow.support.WorkflowHistoryRecorder;
import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.enums.AiChatActorType;
import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import ai.platform.aiassit.chat.history.enums.AiChatDisplayLevel;
import ai.platform.aiassit.chat.history.enums.AiChatMessageType;
import ai.platform.aiassit.chat.history.enums.AiChatRoundType;
import ai.platform.aiassit.chat.history.service.AiChatArtifactService;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.athena.framework.security.auth.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Service
@Slf4j
public class ChatMessageNode extends BaseWorkflowNode {

    private static final String DEFAULT_SESSION_NAME = "新会话";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";

    private final AiChatSessionService sessionService;
    private final AiChatMessageService messageService;
    private final AiChatArtifactService artifactService;
    private final AiChatRoundService roundService;
    private final WorkflowHistoryRecorder historyRecorder;

    public ChatMessageNode(AiChatSessionService sessionService,
                           AiChatMessageService messageService,
                           AiChatArtifactService artifactService,
                           AiChatRoundService roundService,
                           WorkflowHistoryRecorder historyRecorder) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.artifactService = artifactService;
        this.roundService = roundService;
        this.historyRecorder = historyRecorder;
    }

    @Override
    protected NodeResult doExecute(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        if (command == null) {
            return NodeResult.fail("command is required");
        }
        if (!StringUtils.hasText(command.getMessage())) {
            return NodeResult.fail("message is required");
        }

        String sessionCode = command.getSessionCode();
        Long userId = resolveUserId(command.getUserId());

        AiChatSessionDTO session;
        List<AiChatMessageDTO> sessionMessages;
        List<AiChatArtifactDTO> sessionArtifacts;
        if (!StringUtils.hasText(sessionCode)) {
            session = createSession(command, userId);
            sessionMessages = List.of();
            sessionArtifacts = List.of();
            command.setSessionCode(session.getSessionCode());
        } else {
            session = loadSession(sessionCode, userId);
            if (session == null) {
                return NodeResult.fail("session not found");
            }
            sessionMessages = loadSessionMessages(sessionCode, userId);
            sessionArtifacts = loadSessionArtifacts(sessionCode, userId);
        }

        context.setSession(session);
        context.setSessionMessages(sessionMessages);
        context.setSessionArtifacts(sessionArtifacts);
        AiChatRoundDTO round = createRound(session, sessionMessages, command, userId);
        context.setRound(round);

        AiChatMessageDTO lastMessage = sessionMessages.isEmpty() ? null : sessionMessages.get(sessionMessages.size() - 1);
        AiChatMessageDTO userMessage = historyRecorder.saveMessage(
                context,
                round.getRoundCode(),
                "USER",
                AiChatActorType.HUMAN.name(),
                resolveUserMessageType(round.getRoundType()),
                command.getMessage(),
                AiChatContentFormat.PLAIN_TEXT.name(),
                AiChatDisplayLevel.VISIBLE.name(),
                STATUS_SUCCESS,
                lastMessage == null ? null : lastMessage.getMessageCode(),
                lastMessage == null ? null : lastMessage.getMessageCode(),
                null
        );
        context.setCurrentUserMessage(userMessage);
        context.publishEvent("chat-message-ready",
                "session and user message prepared");

        return NodeResult.success(null);
    }

    @Override
    public String type() {
        return "Chat-Message";
    }

    private AiChatSessionDTO createSession(AiChatQueryCommand command, Long userId) {
        AiChatSessionDTO session = new AiChatSessionDTO();
        session.setSessionCode(generateSessionCode());
        session.setUserId(userId);
        session.setBusinessType(resolveBusinessType(command.getBusinessType()));
        session.setSessionName(resolveSessionName(command));
        session.setPinned(Boolean.FALSE);
        return sessionService.add(session);
    }

    private AiChatSessionDTO loadSession(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        return sessionService.get(query);
    }

    private List<AiChatMessageDTO> loadSessionMessages(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        return messageService.queryAll(query).stream()
                .sorted(Comparator.comparing(AiChatMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<AiChatArtifactDTO> loadSessionArtifacts(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        return artifactService.queryAll(query);
    }

    private AiChatRoundDTO createRound(AiChatSessionDTO session,
                                       List<AiChatMessageDTO> sessionMessages,
                                       AiChatQueryCommand command,
                                       Long userId) {
        AiChatRoundDTO round = new AiChatRoundDTO();
        round.setRoundCode(generateRoundCode());
        round.setRoundType(resolveRoundType(command, sessionMessages).name());
        round.setParentRoundCode(resolveParentRoundCode(session.getSessionCode(), userId));
        round.setSessionCode(session.getSessionCode());
        round.setUserId(userId);
        round.setModelCode(resolveModelCode(command.getApiModel()));
        round.setActualModel(resolveActualModel(command.getApiModel()));
        round.setStatus(STATUS_RUNNING);
        return roundService.add(round);
    }

    private Long resolveUserId(Long userId) {
//        return userId == null ? 0L : userId;
        return SecurityContextHolder.get().subject().userId();
    }

    private AiChatBusinessType resolveBusinessType(AiChatBusinessType businessType) {
        return businessType == null ? AiChatBusinessType.GENERAL : businessType;
    }

    private String resolveSessionName(AiChatQueryCommand command) {
        if (StringUtils.hasText(command.getSessionName())) {
            return command.getSessionName().trim();
        }
        if (!StringUtils.hasText(command.getMessage())) {
            return DEFAULT_SESSION_NAME;
        }
        String content = command.getMessage().trim();
        return content.length() > 20 ? content.substring(0, 20) : content;
    }

    private AiChatRoundType resolveRoundType(AiChatQueryCommand command, List<AiChatMessageDTO> sessionMessages) {
        Object extValue = command == null || command.getExt() == null ? null : command.getExt().get("roundType");
        if (extValue instanceof String str && StringUtils.hasText(str)) {
            try {
                return AiChatRoundType.valueOf(str.trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                // fall through to inference
            }
        }
        if (CollectionUtils.isEmpty(sessionMessages)) {
            return AiChatRoundType.USER_QUERY;
        }
        AiChatMessageDTO lastMessage = sessionMessages.get(sessionMessages.size() - 1);
        if (AiChatMessageType.ASSISTANT_QUESTION.name().equals(lastMessage.getMessageType())) {
            return AiChatRoundType.CLARIFICATION;
        }
        return AiChatRoundType.FOLLOW_UP;
    }

    private String resolveUserMessageType(String roundType) {
        if (AiChatRoundType.CLARIFICATION.name().equals(roundType)) {
            return AiChatMessageType.USER_CLARIFICATION.name();
        }
        return AiChatMessageType.USER_INPUT.name();
    }

    private String resolveParentRoundCode(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        List<AiChatRoundDTO> rounds = roundService.queryAll(query);
        if (CollectionUtils.isEmpty(rounds)) {
            return null;
        }
        return rounds.get(rounds.size() - 1).getRoundCode();
    }

    private String resolveModelCode(String apiModel) {
        return StringUtils.hasText(apiModel) ? apiModel.trim() : "DEFAULT";
    }

    private String resolveActualModel(String apiModel) {
        return StringUtils.hasText(apiModel) ? apiModel.trim() : "DEFAULT";
    }

    private String generateSessionCode() {
        return "session-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateRoundCode() {
        return "round-" + UUID.randomUUID().toString().replace("-", "");
    }
}
