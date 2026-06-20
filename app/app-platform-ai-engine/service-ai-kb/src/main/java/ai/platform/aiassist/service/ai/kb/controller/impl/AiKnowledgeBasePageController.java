package ai.platform.aiassist.service.ai.kb.controller.impl;

import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassist.service.ai.kb.controller.req.AiKbPageDocumentListRequest;
import ai.platform.aiassist.service.ai.kb.controller.req.AiKbSyncRequest;
import ai.platform.aiassist.service.ai.kb.controller.resp.AiKbSyncResponse;
import ai.platform.aiassist.service.ai.kb.domainservice.AiKnowledgeBaseManageDomainService;
import ai.platform.aiassist.service.ai.kb.domainservice.AiKnowledgeBaseQueryDomainService;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库页面 controller。
 * 仅先定义前端页面所需接口，后续再接入具体业务实现。
 */
@RestController
public class AiKnowledgeBasePageController implements ai.platform.aiassist.service.ai.kb.controller.AiKnowledgeBasePageController {

    private final AiKnowledgeBaseManageDomainService domainService;
    private final AiKnowledgeBaseQueryDomainService queryDomainService;

    public AiKnowledgeBasePageController(AiKnowledgeBaseManageDomainService domainService,
                                         AiKnowledgeBaseQueryDomainService queryDomainService) {
        this.domainService = domainService;
        this.queryDomainService = queryDomainService;
    }

    @Override
    public List<AiKbInfoDTO> kbList() {
        return queryDomainService.kbList(null);
    }

    @Override
    public List<AiKbDocumentListItemDTO> listDocuments(AiKbPageDocumentListRequest request) {
        ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListRequest payload =
                new ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListRequest();
        if (request != null) {
            payload.setKbCode(request.getKbCode());
        }
        return queryDomainService.listDocuments(payload);
    }

    @Override
    public AiKbDocumentDetailDTO getDocumentDetail(String kbCode, String documentCode) {
        return queryDomainService.getDocumentDetail(kbCode, documentCode);
    }

    @Override
    public R<AiKbDocumentUpsertResponse> upsertDocument(AiKbDocumentUpsertRequest request) {
        return R.ok(domainService.upsertDocument(request));
    }

    @Override
    public R<AiKbSyncResponse> syncDocument(AiKbSyncRequest request) {
        return R.ok(domainService.syncDocument(request));
    }
}
