package ai.platform.aiassit.conversation.service;

import ai.platform.aiassit.conversation.workflow.dto.chat.AiChatQueryCommand;
import ai.platform.aiassit.conversation.dto.chat.AiChatQueryResponse;
import ai.platform.aiassit.conversation.dto.chat.AiChatStreamReconnectRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiChatQueryService {

    AiChatQueryResponse query(AiChatQueryCommand command);

    SseEmitter queryStream(AiChatQueryCommand command);

    SseEmitter reconnectStream(AiChatStreamReconnectRequest request, Long userId, String traceId);
}
