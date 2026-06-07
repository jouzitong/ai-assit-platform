package ai.platform.aiassit.chat.core.query.controller;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequestMapping("/api/v1/ai/chat")
public interface AiChatQueryApi {

    @PostMapping("/query")
    AiChatQueryResponse query(@RequestBody AiChatQueryRequest request);

    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter queryStream(@RequestBody AiChatQueryRequest request);
}
