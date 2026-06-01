package ai.platform.aiassist.service.ai.core.controller;

import ai.platform.aiassist.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassist.service.ai.api.AiKnowledgeBaseExecutionApi;
import ai.platform.aiassist.service.ai.core.AiExecutionDomainService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/execution")
public class AiKnowledgeBaseController implements AiKnowledgeBaseExecutionApi {

    private final AiExecutionDomainService aiExecutionDomainService;

    public AiKnowledgeBaseController(AiExecutionDomainService aiExecutionDomainService) {
        this.aiExecutionDomainService = aiExecutionDomainService;
    }

    @Override
    @PostMapping("/kb/upsert")
    public KbUpsertResponse kbUpsert(@RequestBody KbUpsertRequest request) {
        return aiExecutionDomainService.kbUpsert(request);
    }

    @Override
    @PostMapping("/kb/delete")
    public KbDeleteResponse kbDelete(@RequestBody KbDeleteRequest request) {
        return aiExecutionDomainService.kbDelete(request);
    }

    @Override
    @PostMapping("/kb/search")
    public KbSearchResponse kbSearch(@RequestBody KbSearchRequest request) {
        return aiExecutionDomainService.kbSearch(request);
    }
}
