package ai.platform.aiassist.service.ai.core;

import ai.platform.aiassist.service.ai.api.AiExecutionApi;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassist.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.RerankRequest;
import ai.platform.aiassist.service.ai.api.dto.RerankResponse;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiExecutionController implements AiExecutionApi {

    private final AiExecutionDomainService aiExecutionDomainService;

    public AiExecutionController(AiExecutionDomainService aiExecutionDomainService) {
        this.aiExecutionDomainService = aiExecutionDomainService;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        return aiExecutionDomainService.chat(request);
    }

    @Override
    public EmbedResponse embed(EmbedRequest request) {
        return aiExecutionDomainService.embed(request);
    }

    @Override
    public RerankResponse rerank(RerankRequest request) {
        return aiExecutionDomainService.rerank(request);
    }

    @Override
    public KbUpsertResponse kbUpsert(KbUpsertRequest request) {
        return aiExecutionDomainService.kbUpsert(request);
    }

    @Override
    public KbDeleteResponse kbDelete(KbDeleteRequest request) {
        return aiExecutionDomainService.kbDelete(request);
    }

    @Override
    public KbSearchResponse kbSearch(KbSearchRequest request) {
        return aiExecutionDomainService.kbSearch(request);
    }
}

