package ai.platform.aiassit.conversation.controller.impl;

import ai.platform.aiassit.conversation.service.ConversationService;
import ai.platform.aiassit.conversation.controller.IConversationController;
import ai.platform.aiassit.conversation.dto.conversation.ConversationDetailResponse;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.dto.chat.ConversationQueryRequest;
import ai.platform.aiassit.conversation.dto.chat.ConversationQueryResponse;
import ai.platform.aiassit.conversation.dto.chat.ConversationStreamReconnectRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationDetailRequest;
import ai.platform.aiassit.conversation.service.ConversationExecutionService;
import ai.platform.aiassit.service.ai.api.dto.AiEnabledModelDTO;
import ai.platform.aiassit.model.service.AiModelConfigService;
import lombok.AllArgsConstructor;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.context.SystemContext;
import org.arthena.framework.common.exception.BizException;
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
public class ConversationController implements IConversationController {

    private static final String DEFAULT_SCENE = "ai-chat-query";

    private final ConversationService conversationService;

    private final ConversationExecutionService executionService;

    private final AiModelConfigService aiModelConfigService;

    @Override
    public List<AiEnabledModelDTO> enabledModels() {
        return aiModelConfigService.selectEnabledModels();
    }

    @Override
    public ConversationDetailResponse detail(@RequestBody ConversationDetailRequest request) {
        request.setUserId(resolveCurrentUserId());
        return conversationService.detailConversation(request);
    }

    @Override
    public ConversationQueryResponse completions(@RequestBody ConversationQueryRequest request) {
        return executionService.execute(buildCommand(request));
    }

    @Override
    public SseEmitter completionsStream(@RequestBody ConversationQueryRequest request) {
        return executionService.executeStream(buildCommand(request));
    }

    @Override
    public SseEmitter reconnectStream(@RequestBody ConversationStreamReconnectRequest request) {
        return executionService.reconnectStream(request, resolveCurrentUserId(), resolveTraceId());
    }

    private ConversationQueryCommand buildCommand(ConversationQueryRequest request) {
        ConversationQueryCommand command = new ConversationQueryCommand();
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
        throw BizException.of(ErrCodeConstant.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.value());
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
