package ai.platform.aiassist.service.ai.kb.controller.impl;

import ai.platform.aiassist.service.ai.api.AiKnowledgeBaseManageApi;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassist.service.ai.kb.domainservice.AiKnowledgeBaseManageDomainService;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
public class AiKnowledgeBaseManageController implements AiKnowledgeBaseManageApi {

    private final AiKnowledgeBaseManageDomainService domainService;

    public AiKnowledgeBaseManageController(AiKnowledgeBaseManageDomainService domainService) {
        this.domainService = domainService;
    }

    @Override
    @PostMapping("/internal/v1/ai/kb/document/upsert")
    public R<AiKbDocumentUpsertResponse> upsertDocument(@RequestBody AiKbDocumentUpsertRequest request) {
        return R.ok(domainService.upsertDocument(request));
    }

    @Override
    @PostMapping("/internal/v1/ai/kb/list")
    public List<AiKbInfoDTO> kbList(@RequestBody(required = false) AiKbListRequest request) {
        return domainService.kbList(request);
    }

    @Override
    @PostMapping("/internal/v1/ai/kb/document/list")
    public List<AiKbDocumentListItemDTO> listDocuments(@RequestBody(required = false) AiKbDocumentListRequest request) {
        return domainService.listDocuments(request);
    }

    @Override
    @GetMapping("/internal/v1/ai/kb/document/detail")
    public AiKbDocumentDetailDTO getDocumentDetail(@RequestParam("kbCode") String kbCode,
                                                   @RequestParam("documentCode") String documentCode) {
        return domainService.getDocumentDetail(kbCode, documentCode);
    }
}
