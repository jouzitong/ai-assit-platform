package ai.platform.aiassist.service.ai.core.controller;

import ai.platform.aiassist.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassist.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassist.service.ai.api.dto.RerankRequest;
import ai.platform.aiassist.service.ai.api.dto.RerankResponse;
import ai.platform.aiassist.service.ai.api.AiVectorExecutionApi;
import ai.platform.aiassist.service.ai.core.AiExecutionDomainService;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiVectorController implements AiVectorExecutionApi {

    private final AiExecutionDomainService aiExecutionDomainService;

    public AiVectorController(AiExecutionDomainService aiExecutionDomainService) {
        this.aiExecutionDomainService = aiExecutionDomainService;
    }

    @Override
    public EmbedResponse embed(EmbedRequest request) {
        return aiExecutionDomainService.embed(request);
    }

    @Override
    public RerankResponse rerank(RerankRequest request) {
        return aiExecutionDomainService.rerank(request);
    }
}
