package ai.platform.aiassist.service.ai.core.controller;

import ai.platform.aiassist.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassist.service.ai.api.AiKnowledgeBaseExecutionApi;
import ai.platform.aiassist.service.ai.core.AiExecutionDomainService;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiKnowledgeBaseController implements AiKnowledgeBaseExecutionApi {

    private final AiExecutionDomainService aiExecutionDomainService;

    public AiKnowledgeBaseController(AiExecutionDomainService aiExecutionDomainService) {
        this.aiExecutionDomainService = aiExecutionDomainService;
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
