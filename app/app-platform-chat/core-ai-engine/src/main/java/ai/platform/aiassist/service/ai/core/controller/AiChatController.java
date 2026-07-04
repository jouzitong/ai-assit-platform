package ai.platform.aiassist.service.ai.core.controller;

import ai.platform.aiassist.service.ai.api.AiChatExecutionApi;
import ai.platform.aiassist.service.ai.api.dto.AiEnabledModelDTO;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.stream.ChatChunk;
import ai.platform.aiassist.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassist.service.ai.core.service.AiExecutionDomainService;
import ai.platform.aiassist.service.ai.meta.service.AiModelConfigService;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
public class AiChatController implements AiChatExecutionApi {

    private final AiExecutionDomainService aiExecutionDomainService;
    private final AiModelConfigService aiModelConfigService;

    public AiChatController(AiExecutionDomainService aiExecutionDomainService,
                            AiModelConfigService aiModelConfigService) {
        this.aiExecutionDomainService = aiExecutionDomainService;
        this.aiModelConfigService = aiModelConfigService;
    }

    @Override
    public List<AiEnabledModelDTO> enabledModels() {
        return aiModelConfigService.selectEnabledModels();
    }

    @Override
    public R<ChatResponse> chat(@RequestBody ChatRequest request) {
        return R.ok(aiExecutionDomainService.chat(request));
    }

    @Override
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
