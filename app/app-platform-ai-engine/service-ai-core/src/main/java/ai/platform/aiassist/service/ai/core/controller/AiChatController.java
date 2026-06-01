package ai.platform.aiassist.service.ai.core.controller;

import ai.platform.aiassist.service.ai.api.AiChatExecutionApi;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.stream.ChatChunk;
import ai.platform.aiassist.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassist.service.ai.core.AiExecutionDomainService;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
public class AiChatController implements AiChatExecutionApi {

    private final AiExecutionDomainService aiExecutionDomainService;

    public AiChatController(AiExecutionDomainService aiExecutionDomainService) {
        this.aiExecutionDomainService = aiExecutionDomainService;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        return aiExecutionDomainService.chat(request);
    }

    @Override
    public SseEmitter chatStream(ChatRequest request) {
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
