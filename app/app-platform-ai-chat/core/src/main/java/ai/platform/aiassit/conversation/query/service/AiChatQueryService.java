package ai.platform.aiassit.conversation.query.service;

import ai.platform.aiassit.conversation.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.conversation.query.dto.AiChatQueryResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiChatQueryService {

    AiChatQueryResponse query(AiChatQueryCommand command);

    SseEmitter queryStream(AiChatQueryCommand command);
}
