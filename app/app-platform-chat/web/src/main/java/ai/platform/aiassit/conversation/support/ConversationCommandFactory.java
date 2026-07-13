package ai.platform.aiassit.conversation.support;

import ai.platform.aiassit.conversation.dto.chat.ConversationQueryRequest;
import ai.platform.aiassit.conversation.protocol.dto.ChatTransportRequest;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.model.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.model.service.AiModelConfigService;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ConversationCommandFactory {

    private static final String DEFAULT_SCENE = "ai-chat-query";

    private final AiModelConfigService modelConfigService;

    public ConversationCommandFactory(AiModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    public ConversationQueryCommand fromLegacy(ConversationQueryRequest request, Long userId, String traceId) {
        ConversationQueryCommand command = base(userId, traceId);
        command.setSessionCode(request == null ? null : request.getSessionCode());
        applyModel(command, request == null ? null : request.getModelId());
        command.setMessage(request == null ? null : request.getMessage());
        command.setAttachments(request == null || request.getAttachments() == null ? List.of() : request.getAttachments());
        command.setTools(request == null || request.getTools() == null ? List.of() : request.getTools());
        command.setExt(request == null || request.getExt() == null ? Map.of() : request.getExt());
        command.setSessionName(resolveSessionName(command.getMessage()));
        return command;
    }

    public ConversationQueryCommand fromProtocol(ChatTransportRequest request,
                                                 String pathSessionCode,
                                                 Long userId,
                                                 String fallbackTraceId) {
        ConversationQueryCommand command = base(userId,
                request != null && StringUtils.hasText(request.getRequestId())
                        ? request.getRequestId()
                        : fallbackTraceId);
        command.setSessionCode(StringUtils.hasText(pathSessionCode)
                ? pathSessionCode
                : request == null ? null : request.getSessionCode());
        command.setRoundCode(request == null ? null : request.getRoundCode());
        applyModel(command, request == null ? null : request.getModelId());
        command.setMessage(request == null || request.getMessage() == null ? null : request.getMessage().text());
        Map<String, Object> ext = new LinkedHashMap<>();
        if (request != null && request.getClientContext() != null) {
            ext.put("clientContext", request.getClientContext());
        }
        if (request != null && request.getMessage() != null) {
            ext.put("clientMessageId", request.getMessage().getId());
            ext.put("clientMessageCreatedAt", request.getMessage().getCreatedAt());
        }
        command.setExt(ext);
        command.setSessionName(resolveSessionName(command.getMessage()));
        return command;
    }

    private void applyModel(ConversationQueryCommand command, Long modelId) {
        command.setModelId(modelId);
        if (modelId == null) {
            return;
        }
        AiModelConfigDTO config = modelConfigService.getResolvedById(modelId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled()) || !StringUtils.hasText(config.getModelCode())) {
            throw BizException.of(AiChatBizCodeConstant.MODEL_CONFIG_NOT_FOUND, modelId);
        }
        command.setApiModel(config.getModelCode());
        command.setActualModel(config.getApiModel());
    }

    private ConversationQueryCommand base(Long userId, String traceId) {
        ConversationQueryCommand command = new ConversationQueryCommand();
        command.setScene(DEFAULT_SCENE);
        command.setTraceId(traceId);
        command.setUserId(userId);
        return command;
    }

    private String resolveSessionName(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() <= 24 ? trimmed : trimmed.substring(0, 24);
    }
}
