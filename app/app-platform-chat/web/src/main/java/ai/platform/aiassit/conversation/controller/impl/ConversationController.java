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
import ai.platform.aiassit.model.service.AiModelConfigService;
import ai.platform.aiassit.service.ai.api.dto.AiEnabledModelDTO;
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

}
