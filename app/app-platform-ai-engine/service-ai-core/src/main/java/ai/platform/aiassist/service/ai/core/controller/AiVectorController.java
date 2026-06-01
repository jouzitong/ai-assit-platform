package ai.platform.aiassist.service.ai.core.controller;

import ai.platform.aiassist.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassist.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassist.service.ai.api.dto.RerankRequest;
import ai.platform.aiassist.service.ai.api.dto.RerankResponse;
import ai.platform.aiassist.service.ai.api.AiVectorExecutionApi;
import ai.platform.aiassist.service.ai.core.AiExecutionDomainService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/execution")
public class AiVectorController implements AiVectorExecutionApi {

    private final AiExecutionDomainService aiExecutionDomainService;

    public AiVectorController(AiExecutionDomainService aiExecutionDomainService) {
        this.aiExecutionDomainService = aiExecutionDomainService;
    }

    @Override
    @PostMapping("/vector/embed")
    public EmbedResponse embed(@RequestBody EmbedRequest request) {
        return aiExecutionDomainService.embed(request);
    }

    @Override
    @PostMapping("/vector/rerank")
    public RerankResponse rerank(@RequestBody RerankRequest request) {
        return aiExecutionDomainService.rerank(request);
    }
}
