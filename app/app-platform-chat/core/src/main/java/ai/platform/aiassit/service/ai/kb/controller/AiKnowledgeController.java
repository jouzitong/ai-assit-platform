package ai.platform.aiassit.service.ai.kb.controller;

import ai.platform.aiassit.service.ai.api.AiKnowledgeApi;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentContentUpdateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassit.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassit.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassit.service.ai.core.service.AiExecutionDomainService;
import ai.platform.aiassit.service.ai.kb.domainservice.AiKnowledgeDomainService;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiKnowledgeController implements AiKnowledgeApi {

    private final AiKnowledgeDomainService domainService;
    private final AiExecutionDomainService aiExecutionDomainService;

    public AiKnowledgeController(AiKnowledgeDomainService domainService,
                                 AiExecutionDomainService aiExecutionDomainService) {
        this.domainService = domainService;
        this.aiExecutionDomainService = aiExecutionDomainService;
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
        return aiExecutionDomainService.kbDelete(request);
    }

    @Override
    public KbSearchResponse kbSearch(@RequestBody KbSearchRequest request) {
        return aiExecutionDomainService.kbSearch(request);
    }
}
