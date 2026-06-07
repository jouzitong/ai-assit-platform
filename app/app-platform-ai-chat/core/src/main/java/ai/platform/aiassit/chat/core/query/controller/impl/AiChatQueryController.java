package ai.platform.aiassit.chat.core.query.controller.impl;

import ai.platform.aiassit.chat.core.query.AiChatQueryService;
import ai.platform.aiassit.chat.core.query.controller.AiChatQueryApi;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/ai/chat")
@AllArgsConstructor
public class AiChatQueryController implements AiChatQueryApi {

    private final AiChatQueryService service;

    @Override
    @PostMapping("/query")
    public AiChatQueryResponse query(@RequestBody AiChatQueryRequest request) {
        return service.query(request);
    }

    @Override
    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryStream(@RequestBody AiChatQueryRequest request) {
        return service.queryStream(request);
    }
}
