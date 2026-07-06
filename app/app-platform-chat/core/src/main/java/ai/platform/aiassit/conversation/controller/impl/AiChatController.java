package ai.platform.aiassit.conversation.controller.impl;

import ai.platform.aiassit.conversation.service.AiChatConversationService;
import ai.platform.aiassit.conversation.controller.IAiChatController;
import ai.platform.aiassit.conversation.dto.conversation.AiChatConversationDetailResponse;
import ai.platform.aiassit.conversation.workflow.dto.chat.AiChatQueryCommand;
import ai.platform.aiassit.conversation.dto.chat.AiChatQueryRequest;
import ai.platform.aiassit.conversation.dto.chat.AiChatQueryResponse;
import ai.platform.aiassit.conversation.dto.chat.AiChatStreamReconnectRequest;
import ai.platform.aiassit.conversation.dto.conversation.AiChatConversationDetailRequest;
import ai.platform.aiassit.conversation.service.AiChatQueryService;
import ai.platform.aiassit.service.ai.api.dto.AiEnabledModelDTO;
import ai.platform.aiassit.model.service.AiModelConfigService;
import lombok.AllArgsConstructor;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.context.SystemContext;
import org.arthena.framework.common.exception.base.BaseHttpRuntimeException;
import org.athena.framework.security.api.model.UserContext;
import org.springframework.http.HttpStatus;
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
public class AiChatController implements IAiChatController {

    private static final String DEFAULT_SCENE = "ai-chat-query";

    private final AiChatConversationService conversationService;

    private final AiChatQueryService queryService;

    private final AiModelConfigService aiModelConfigService;

    @Override
    public List<AiEnabledModelDTO> enabledModels() {
        return aiModelConfigService.selectEnabledModels();
    }

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
        throw new BaseHttpRuntimeException(HttpStatus.UNAUTHORIZED.value(), ErrCodeConstant.UNAUTHORIZED);
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
