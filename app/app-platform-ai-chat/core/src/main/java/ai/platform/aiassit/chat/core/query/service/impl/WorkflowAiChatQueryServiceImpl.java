package ai.platform.aiassit.chat.core.query.service.impl;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.query.service.AiChatQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
//@Service
public class WorkflowAiChatQueryServiceImpl implements AiChatQueryService {

    @Override
    public AiChatQueryResponse query(AiChatQueryCommand command) {
        return null;
    }

    @Override
    public SseEmitter queryStream(AiChatQueryCommand command) {
        return null;
    }
}
