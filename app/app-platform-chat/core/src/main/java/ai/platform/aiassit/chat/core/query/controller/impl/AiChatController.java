package ai.platform.aiassit.chat.core.query.controller.impl;

import ai.platform.aiassit.chat.core.conversation.service.AiChatConversationService;
import ai.platform.aiassit.chat.core.query.controller.AiChatApi;
import ai.platform.aiassit.chat.core.query.dto.AiChatConversationDetailResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatStreamReconnectRequest;
import ai.platform.aiassit.chat.core.query.req.AiChatConversationDetailRequest;
import ai.platform.aiassit.chat.core.query.service.AiChatQueryService;
import lombok.AllArgsConstructor;
import org.arthena.framework.common.context.SystemContext;
import org.athena.framework.security.api.model.UserContext;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@AllArgsConstructor
public class AiChatController implements AiChatApi {

    private static final String DEFAULT_SCENE = "ai-chat-query";

    private final AiChatConversationService conversationService;

    private final AiChatQueryService queryService;

    @Override
    public AiChatConversationDetailResponse detail(@RequestBody AiChatConversationDetailRequest request) {
        request.setUserId(resolveCurrentUserId());
        return conversationService.detailConversation(request);
    }

    @Override
    public AiChatQueryResponse completions(@RequestBody AiChatQueryRequest request) {
        return queryService.query(buildCommand(request));
    }

    @Override
    public SseEmitter completionsStream(@RequestBody AiChatQueryRequest request) {
        return queryService.queryStream(buildCommand(request));
    }

    @Override
    public SseEmitter reconnectStream(@RequestBody AiChatStreamReconnectRequest request) {
        return queryService.reconnectStream(request, resolveCurrentUserId(), resolveTraceId());
    }

    private AiChatQueryCommand buildCommand(AiChatQueryRequest request) {
        AiChatQueryCommand command = new AiChatQueryCommand();
        command.setSessionCode(request == null ? null : request.getSessionCode());
        command.setApiModel(request == null ? null : request.getApiModel());
        command.setMessage(request == null ? null : request.getMessage());
        command.setAttachments(request == null || request.getAttachments() == null ? List.of() : request.getAttachments());
        command.setTools(request == null || request.getTools() == null ? List.of() : request.getTools());
        command.setExt(request == null || request.getExt() == null ? Map.of() : request.getExt());
        command.setScene(DEFAULT_SCENE);
        command.setTraceId(resolveTraceId());
        command.setUserId(resolveCurrentUserId());
        command.setSessionName(resolveSessionName(command.getMessage()));
        return command;
    }

    private String resolveTraceId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String traceId = attributes.getRequest().getHeader("traceId");
            if (traceId == null || traceId.isBlank()) {
                traceId = attributes.getRequest().getHeader("X-Trace-Id");
            }
            if (traceId != null && !traceId.isBlank()) {
                return traceId.trim();
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Long resolveCurrentUserId() {
        UserContext userContext = SystemContext.getUserContext();
        if (userContext != null && userContext.subject() != null) {
            return userContext.subject().userId();
        }
        throw new IllegalArgumentException("current user is required");
    }

    private String resolveSessionName(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 24 ? trimmed : trimmed.substring(0, 24);
    }
}
