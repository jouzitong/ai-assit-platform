package ai.platform.aiassist.service.ai.kb.controller.impl;

import ai.platform.aiassist.service.ai.api.AiKnowledgeApi;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.core.service.AiExecutionDomainService;
import ai.platform.aiassist.service.ai.kb.domainservice.AiKnowledgeBaseManageDomainService;
import ai.platform.aiassist.service.ai.kb.domainservice.AiKnowledgeBaseQueryDomainService;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class AiKnowledgeController implements AiKnowledgeApi {

    private final AiKnowledgeBaseManageDomainService domainService;
    private final AiKnowledgeBaseQueryDomainService queryDomainService;
    private final AiExecutionDomainService aiExecutionDomainService;

    public AiKnowledgeController(AiKnowledgeBaseManageDomainService domainService,
                                 AiKnowledgeBaseQueryDomainService queryDomainService,
                                 AiExecutionDomainService aiExecutionDomainService) {
        this.domainService = domainService;
        this.queryDomainService = queryDomainService;
        this.aiExecutionDomainService = aiExecutionDomainService;
    }

    @Override
    @PostMapping("/internal/v1/ai/kb/document/upsert")
    public R<AiKbDocumentUpsertResponse> upsertDocument(@RequestBody AiKbDocumentUpsertRequest request) {
        return R.ok(domainService.upsertDocument(request));
    }

    @Override
    @PostMapping("/internal/v1/ai/kb/id")
    public R<String> getKbId(@RequestBody(required = false) AiKbListRequest request) {
        return R.ok(queryDomainService.getKbId(request));
    }

    @Override
    @PostMapping("/api/v1/ai/execution/kb/delete")
    public KbDeleteResponse kbDelete(@RequestBody KbDeleteRequest request) {
        return aiExecutionDomainService.kbDelete(request);
    }

    @Override
    @PostMapping("/api/v1/ai/execution/kb/search")
    public KbSearchResponse kbSearch(@RequestBody KbSearchRequest request) {
        return aiExecutionDomainService.kbSearch(request);
    }
}
