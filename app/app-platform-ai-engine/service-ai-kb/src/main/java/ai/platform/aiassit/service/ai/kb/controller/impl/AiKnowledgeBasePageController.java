package ai.platform.aiassit.service.ai.kb.controller.impl;

import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentContentUpdateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.AiKbCreateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassit.service.ai.kb.controller.req.AiKbDeleteRequest;
import ai.platform.aiassit.service.ai.kb.controller.req.AiKbPageDocumentListRequest;
import ai.platform.aiassit.service.ai.kb.controller.req.AiKbSyncCheckRequest;
import ai.platform.aiassit.service.ai.kb.controller.req.AiKbSyncRequest;
import ai.platform.aiassit.service.ai.kb.controller.resp.AiKbDeleteResponse;
import ai.platform.aiassit.service.ai.kb.controller.resp.AiKbSyncCheckResponse;
import ai.platform.aiassit.service.ai.kb.controller.resp.AiKbSyncResponse;
import ai.platform.aiassit.service.ai.kb.domainservice.AiKnowledgeDomainService;
import org.athena.framework.data.jdbc.vo.PageResultVO;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库页面 controller。
 * 仅先定义前端页面所需接口，后续再接入具体业务实现。
 */
@RestController
public class AiKnowledgeBasePageController implements ai.platform.aiassit.service.ai.kb.controller.AiKnowledgeBasePageController {

    private final AiKnowledgeDomainService domainService;

    public AiKnowledgeBasePageController(AiKnowledgeDomainService domainService) {
        this.domainService = domainService;
    }

    @Override
    public List<AiKbInfoDTO> kbList() {
        return domainService.kbList(null);
    }

    @Override
    public R<AiKbInfoDTO> createKnowledgeBase(AiKbCreateRequest request) {
        return R.ok(domainService.createKnowledgeBase(request));
    }

    @Override
    public PageResultVO<AiKbDocumentListItemDTO> listDocuments(AiKbPageDocumentListRequest request) {
        AiKbDocumentListRequest payload =
                new AiKbDocumentListRequest();
        if (request != null) {
            payload.setKbCode(request.getKbCode());
            payload.setKeyword(request.getKeyword());
            payload.setBizTypeCode(request.getBizTypeCode());
            payload.setTab(request.getTab());
            payload.setPage(request.getPage());
            payload.setSize(request.getSize());
        }
        return domainService.listDocuments(payload);
    }

    @Override
    public AiKbDocumentDetailDTO getDocumentDetail(String kbCode, String documentCode) {
        return domainService.getDocumentDetail(kbCode, documentCode);
    }

    @Override
    public R<AiKbDocumentUpsertResponse> upsertDocument(AiKbDocumentUpsertRequest request) {
        return R.ok(domainService.upsertDocument(request));
    }

    @Override
    public R<AiKbDocumentUpsertResponse> updateDocumentContent(AiKbDocumentContentUpdateRequest request) {
        return R.ok(domainService.updateDocumentContent(request));
    }

    @Override
    public R<AiKbSyncResponse> syncDocument(AiKbSyncRequest request) {
        return R.ok(domainService.syncDocument(request));
    }

    @Override
    public R<AiKbSyncCheckResponse> checkDocumentSync(AiKbSyncCheckRequest request) {
        return R.ok(domainService.checkDocumentSync(request));
    }

    @Override
    public R<AiKbDeleteResponse> deleteDocument(AiKbDeleteRequest request) {
        return R.ok(domainService.deleteDocument(request));
    }
}
