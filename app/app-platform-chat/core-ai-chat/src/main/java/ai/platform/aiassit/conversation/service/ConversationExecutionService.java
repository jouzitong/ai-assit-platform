package ai.platform.aiassit.conversation.service;

import ai.platform.aiassit.conversation.dto.chat.ConversationQueryResponse;
import ai.platform.aiassit.conversation.dto.chat.ConversationStreamReconnectRequest;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ConversationExecutionService {

    ConversationQueryResponse execute(ConversationQueryCommand command);

    SseEmitter executeStream(ConversationQueryCommand command);

    SseEmitter reconnectStream(ConversationStreamReconnectRequest request, Long userId, String traceId);
}
