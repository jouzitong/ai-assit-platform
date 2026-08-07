package ai.platform.aiassit.conversation.support;

import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
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
    private static final String HOME_CHAT_ENTRY = "HOME_CHAT";
    private static final String SETTINGS_ASSISTANT_SCENE = "SETTINGS_ASSISTANT";
    private static final String SETTINGS_ASSISTANT_ENTRY = "SETTINGS_ASSISTANT";

    private final AiModelConfigService modelConfigService;

    public ConversationCommandFactory(AiModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    public ConversationQueryCommand fromLegacy(ConversationQueryRequest request, Long userId, String traceId) {
        ConversationQueryCommand command = base(userId, traceId);
        command.setSessionCode(request == null ? null : request.getSessionCode());
        command.setGroupCode(request == null ? null : request.getGroupCode());
        applyModel(command, request == null ? null : request.getModelId());
        command.setAgentEntryCode(HOME_CHAT_ENTRY);
        command.setMessage(request == null ? null : request.getMessage());
        command.setAttachments(request == null || request.getAttachments() == null ? List.of() : request.getAttachments());
        command.setTools(request == null || request.getTools() == null ? List.of() : request.getTools());
        Map<String, Object> ext = new LinkedHashMap<>(
                request == null || request.getExt() == null ? Map.of() : request.getExt());
        command.setExt(ext);
        command.setSessionName(resolveSessionName(command.getMessage()));
        return command;
    }

    private void applyAgentTarget(ConversationQueryCommand command, ChatTransportRequest.Target target) {
        if (target == null) {
            return;
        }
        if (StringUtils.hasText(target.getType()) && !"AGENT".equalsIgnoreCase(target.getType().trim())) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_QUERY_COMMAND);
        }
        if (StringUtils.hasText(target.getAgentCode())) {
            command.setAgentCode(target.getAgentCode().trim());
            command.setAgentVersion(target.getAgentVersion());
        }
    }

    public ConversationQueryCommand fromProtocol(ChatTransportRequest request,
                                                 String pathSessionCode,
                                                 Long userId,
                                                 String fallbackTraceId) {
        return fromProtocol(request, pathSessionCode, userId, fallbackTraceId, false);
    }

    /**
     * Builds a command for the system-settings floating assistant.
     *
     * <p>The channel, Agent entry and session business type are server-owned. A caller cannot
     * pin an arbitrary Agent through this endpoint.</p>
     */
    public ConversationQueryCommand fromSettingsAssistantProtocol(ChatTransportRequest request,
                                                                   String pathSessionCode,
                                                                   Long userId,
                                                                   String fallbackTraceId,
                                                                   boolean allowModelOverride) {
        if (request != null && request.getTarget() != null) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_QUERY_COMMAND);
        }
        if (request != null && StringUtils.hasText(request.getGroupCode())) {
            throw BizException.of(AiChatBizCodeConstant.GROUP_ASSIGNMENT_NOT_ALLOWED,
                    request.getGroupCode().trim());
        }
        ConversationQueryCommand command = fromProtocol(
                request, pathSessionCode, userId, fallbackTraceId, allowModelOverride);
        command.setScene(SETTINGS_ASSISTANT_SCENE);
        command.setAgentEntryCode(SETTINGS_ASSISTANT_ENTRY);
        command.setBusinessType(ConversationBusinessType.PAGE_ASSISTANT);
        return command;
    }

    public ConversationQueryCommand fromProtocol(ChatTransportRequest request,
                                                  String pathSessionCode,
                                                  Long userId,
                                                  String fallbackTraceId,
                                                  boolean allowModelOverride) {
        ConversationQueryCommand command = base(userId,
                request != null && StringUtils.hasText(request.getRequestId())
                        ? request.getRequestId()
                        : fallbackTraceId);
        command.setSessionCode(StringUtils.hasText(pathSessionCode)
                ? pathSessionCode
                : request == null ? null : request.getSessionCode());
        command.setGroupCode(request == null ? null : request.getGroupCode());
        command.setRoundCode(request == null ? null : request.getRoundCode());
        Long modelId = request == null ? null : request.getModelId();
        Long modelOverrideId = request == null ? null : request.getModelOverrideId();
        if (modelOverrideId != null) {
            if (!allowModelOverride) {
                throw BizException.of(AiChatBizCodeConstant.AGENT_EXECUTION_FAILED,
                        "Model override is not permitted for this Agent conversation");
            }
            modelId = modelOverrideId;
        }
        applyModel(command, modelId);
        applyAgentTarget(command, request == null ? null : request.getTarget());
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
        command.setAgentEntryCode(HOME_CHAT_ENTRY);
        command.setBusinessType(ConversationBusinessType.CUSTOM);
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
