package ai.platform.aiassist.service.ai.api;

import ai.platform.aiassist.service.ai.api.dto.AiEnabledModelDTO;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import org.athena.framework.web.annotation.IgnoredResultWrapper;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 对话执行 API（HTTP/Feign）。
 */
@FeignClient(
        name = "app-platform-ai-engine",
        contextId = "platformAiEngineClient",
        path = "/aiEngine"
)
public interface AiChatExecutionApi {

    @GetMapping("/api/v1/ai/models/enable")
    @IgnoredResultWrapper
    List<AiEnabledModelDTO> enabledModels();

    @PostMapping("/api/v1/ai/execution/chat")
    @IgnoredResultWrapper
    ChatResponse chat(@RequestBody ChatRequest request);

    @PostMapping(value = "/api/v1/ai/execution/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter chatStream(@RequestBody ChatRequest request);
}
