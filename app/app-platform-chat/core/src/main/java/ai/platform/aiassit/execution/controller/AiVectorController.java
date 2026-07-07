package ai.platform.aiassit.execution.controller;

import ai.platform.aiassit.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassit.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassit.service.ai.api.dto.RerankRequest;
import ai.platform.aiassit.service.ai.api.dto.RerankResponse;
import ai.platform.aiassit.service.ai.api.AiVectorExecutionApi;
import ai.platform.aiassit.execution.service.AiKnowledgeExecutionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/execution")
public class AiVectorController implements AiVectorExecutionApi {

    private final AiKnowledgeExecutionService aiKnowledgeExecutionService;

    public AiVectorController(AiKnowledgeExecutionService aiKnowledgeExecutionService) {
        this.aiKnowledgeExecutionService = aiKnowledgeExecutionService;
    }

    @Override
    @PostMapping("/vector/embed")
    public EmbedResponse embed(@RequestBody EmbedRequest request) {
        return aiKnowledgeExecutionService.embed(request);
    }

    @Override
    @PostMapping("/vector/rerank")
    public RerankResponse rerank(@RequestBody RerankRequest request) {
        return aiKnowledgeExecutionService.rerank(request);
    }
}
