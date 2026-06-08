package ai.platform.aiassit.chat.core.workflow.node.impl;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.node.BaseWorkflowNode;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
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

    private final AiChatSessionService sessionService;
    private final AiChatMessageService messageService;

    public ChatMessageNode(AiChatSessionService sessionService, AiChatMessageService messageService) {
        this.sessionService = sessionService;
        this.messageService = messageService;
    }

    @Override
    protected NodeResult doExecute(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        if (command == null) {
            return NodeResult.fail("command is required");
        }

        String sessionCode = command.getSessionCode();
        Long userId = resolveUserId(command.getUserId());

        AiChatSessionDTO session;
        List<AiChatMessageDTO> sessionMessages;
        if (!StringUtils.hasText(sessionCode)) {
            session = createSession(command, userId);
            sessionMessages = List.of();
            command.setSessionCode(session.getSessionCode());
        } else {
            session = loadSession(sessionCode, userId);
            if (session == null) {
                return NodeResult.fail("session not found");
            }
            sessionMessages = loadSessionMessages(sessionCode, userId);
        }

        context.setSession(session);
        context.setSessionMessages(sessionMessages);

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
        query.setUserId(userId);
        return sessionService.get(query);
    }

    private List<AiChatMessageDTO> loadSessionMessages(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setUserId(userId);
        return messageService.queryAll(query).stream()
                .sorted(Comparator.comparing(AiChatMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private Long resolveUserId(Long userId) {
        return userId == null ? 0L : userId;
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

    private String generateSessionCode() {
        return "session-" + UUID.randomUUID().toString().replace("-", "");
    }
}
