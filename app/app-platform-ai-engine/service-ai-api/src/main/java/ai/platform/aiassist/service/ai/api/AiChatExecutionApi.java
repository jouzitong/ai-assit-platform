package ai.platform.aiassist.service.ai.api;

import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话执行 API（HTTP/Feign）。
 */
@FeignClient(
        name = "${app.ai-engine.name:boot-ai-engine}",
        url = "${app.ai-engine.url:http://127.0.0.1:13101}"
)
public interface AiChatExecutionApi {

    @PostMapping("/api/v1/ai/execution/chat")
    ChatResponse chat(@RequestBody ChatRequest request);

    @PostMapping(value = "/api/v1/ai/execution/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter chatStream(@RequestBody ChatRequest request);
}
