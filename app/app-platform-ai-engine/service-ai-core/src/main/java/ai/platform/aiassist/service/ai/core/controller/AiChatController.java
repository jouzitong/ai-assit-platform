package ai.platform.aiassist.service.ai.core.controller;

import ai.platform.aiassist.service.ai.api.AiChatExecutionApi;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.stream.ChatChunk;
import ai.platform.aiassist.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassist.service.ai.core.AiExecutionDomainService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/ai/execution")
public class AiChatController implements AiChatExecutionApi {

    private final AiExecutionDomainService aiExecutionDomainService;

    public AiChatController(AiExecutionDomainService aiExecutionDomainService) {
        this.aiExecutionDomainService = aiExecutionDomainService;
    }

    @Override
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return aiExecutionDomainService.chat(request);
    }

    @Override
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        aiExecutionDomainService.chatStreamAsync(request, new ChatStreamObserver() {
            @Override
            public void onChunk(ChatChunk chunk) {
                try {
                    emitter.send(SseEmitter.event().name("chunk").data(chunk));
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }

            @Override
            public void onComplete() {
                emitter.complete();
            }

            @Override
            public void onError(Throwable throwable) {
                emitter.completeWithError(throwable);
            }
        });
        return emitter;
    }
}
