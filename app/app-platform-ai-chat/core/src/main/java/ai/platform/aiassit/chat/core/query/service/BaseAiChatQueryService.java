package ai.platform.aiassit.chat.core.query.service;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
public abstract class BaseAiChatQueryService implements AiChatQueryService{

    @Override
    public SseEmitter queryStream(AiChatQueryCommand command) {
        return null;
    }

    @Override
    public AiChatQueryResponse query(AiChatQueryCommand command) {
        return null;
    }




}
