package ai.platform.aiassit.chat.core.query.service;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiChatQueryService {

    AiChatQueryResponse query(AiChatQueryCommand command);

    SseEmitter queryStream(AiChatQueryCommand command);
}
