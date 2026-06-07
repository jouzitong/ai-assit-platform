package ai.platform.aiassit.chat.core.query;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiChatQueryService {

    AiChatQueryResponse query(AiChatQueryRequest request);

    SseEmitter queryStream(AiChatQueryRequest request);
}
