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
import ai.platform.aiassit.conversation.transport.sse.SseConversationTransport;
import ai.platform.aiassit.conversation.support.ConversationCommandFactory;
import ai.platform.aiassit.conversation.support.ConversationRequestContextResolver;
import ai.platform.aiassit.model.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.model.service.AiModelConfigService;
import ai.platform.aiassit.service.ai.api.dto.AiBrowserAgentModelDTO;
import ai.platform.aiassit.service.ai.api.dto.AiEnabledModelDTO;
import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@AllArgsConstructor
@Slf4j
public class ConversationController implements IConversationController {

    private final ConversationService conversationService;

    private final ConversationExecutionService executionService;

    private final SseConversationTransport sseTransport;

    private final AiModelConfigService aiModelConfigService;

    private final ConversationCommandFactory commandFactory;

    private final ConversationRequestContextResolver contextResolver;

    @Override
    public List<AiEnabledModelDTO> enabledModels() {
        return aiModelConfigService.selectEnabledModels();
    }

    @Override
    public List<AiBrowserAgentModelDTO> browserAgentModels() {
        return aiModelConfigService.selectEnabledModels().stream()
                .filter(model -> model.getClientType() == AiChatClientType.SPRING_AI)
                .map(AiEnabledModelDTO::getId)
                .map(aiModelConfigService::getResolvedById)
                .filter(this::isAvailableBrowserAgentModel)
                .map(this::toBrowserAgentModel)
                .toList();
    }

    @Override
    public ConversationDetailResponse detail(@RequestBody ConversationDetailRequest request) {
        request.setUserId(contextResolver.currentUserId());
        return conversationService.detailConversation(request);
    }

    @Override
    public ConversationQueryResponse completions(@RequestBody ConversationQueryRequest request) {
        return executionService.execute(buildCommand(request));
    }

    @Override
    public SseEmitter completionsStream(@RequestBody ConversationQueryRequest request) {
        ConversationQueryCommand command = buildCommand(request);
        log.info("接收到对话流式请求，command={}", command);
        return sseTransport.start(command);
    }

    @Override
    public SseEmitter reconnectStream(@RequestBody ConversationStreamReconnectRequest request) {
        return sseTransport.reconnect(request, contextResolver.currentUserId(), contextResolver.traceId());
    }

    private ConversationQueryCommand buildCommand(ConversationQueryRequest request) {
        return commandFactory.fromLegacy(request, contextResolver.currentUserId(), contextResolver.traceId());
    }

    private boolean isAvailableBrowserAgentModel(AiModelConfigDTO model) {
        return model != null
                && Boolean.TRUE.equals(model.getEnabled())
                && model.getClientType() == AiChatClientType.SPRING_AI;
    }

    private AiBrowserAgentModelDTO toBrowserAgentModel(AiModelConfigDTO model) {
        AiBrowserAgentModelDTO result = new AiBrowserAgentModelDTO();
        result.setId(model.getId());
        result.setModelCode(model.getModelCode());
        result.setModelName(model.getModelName());
        result.setApiModel(model.getApiModel());
        result.setClientType(model.getClientType());
        result.setBaseUrl(model.getBaseUrl());
        result.setApiKey(model.getApiKey());
        return result;
    }
}
