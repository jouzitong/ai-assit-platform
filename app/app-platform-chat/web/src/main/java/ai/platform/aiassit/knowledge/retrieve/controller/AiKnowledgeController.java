package ai.platform.aiassit.knowledge.retrieve.controller;

import ai.platform.aiassit.service.ai.api.AiKnowledgeApi;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentContentUpdateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassit.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassit.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassit.execution.service.AiKnowledgeExecutionService;
import ai.platform.aiassit.knowledge.manage.domainservice.AiKnowledgeManageDomainService;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiKnowledgeController implements AiKnowledgeApi {

    private final AiKnowledgeManageDomainService domainService;
    private final AiKnowledgeExecutionService aiKnowledgeExecutionService;

    public AiKnowledgeController(AiKnowledgeManageDomainService domainService,
                                 AiKnowledgeExecutionService aiKnowledgeExecutionService) {
        this.domainService = domainService;
        this.aiKnowledgeExecutionService = aiKnowledgeExecutionService;
    }

    @Override
    public R<AiKbDocumentUpsertResponse> upsertDocument(@RequestBody AiKbDocumentUpsertRequest request) {
        return R.ok(domainService.upsertDocument(request));
    }

    @Override
    public R<AiKbDocumentUpsertResponse> updateDocumentContent(@RequestBody AiKbDocumentContentUpdateRequest request) {
        return R.ok(domainService.updateDocumentContent(request));
    }

    @Override
    public R<String> getKbId(@RequestBody(required = false) AiKbListRequest request) {
        return R.ok(domainService.getKbId(request));
    }

    @Override
    public KbDeleteResponse kbDelete(@RequestBody KbDeleteRequest request) {
        return aiKnowledgeExecutionService.kbDelete(request);
    }

    @Override
    public KbSearchResponse kbSearch(@RequestBody KbSearchRequest request) {
        return aiKnowledgeExecutionService.kbSearch(request);
    }
}
