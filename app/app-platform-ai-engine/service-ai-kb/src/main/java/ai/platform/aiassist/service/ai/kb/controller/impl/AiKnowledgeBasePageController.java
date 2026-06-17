package ai.platform.aiassist.service.ai.kb.controller.impl;

import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassist.service.ai.kb.controller.req.AiKbPageDocumentListRequest;
import ai.platform.aiassist.service.ai.kb.controller.req.AiKbSyncRequest;
import ai.platform.aiassist.service.ai.kb.controller.resp.AiKbSyncResponse;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库页面 controller。
 * 仅先定义前端页面所需接口，后续再接入具体业务实现。
 */
@RestController
public class AiKnowledgeBasePageController implements ai.platform.aiassist.service.ai.kb.controller.AiKnowledgeBasePageController {

    @Override
    public List<AiKbInfoDTO> kbList() {
        throw new UnsupportedOperationException("TODO: implement kbList");
    }

    @Override
    public List<AiKbDocumentListItemDTO> listDocuments(AiKbPageDocumentListRequest request) {
        throw new UnsupportedOperationException("TODO: implement listDocuments");
    }

    @Override
    public AiKbDocumentDetailDTO getDocumentDetail(String kbCode, String documentCode) {
        throw new UnsupportedOperationException("TODO: implement getDocumentDetail");
    }

    @Override
    public R<AiKbDocumentUpsertResponse> upsertDocument(AiKbDocumentUpsertRequest request) {
        throw new UnsupportedOperationException("TODO: implement upsertDocument");
    }

    @Override
    public R<AiKbSyncResponse> syncDocument(AiKbSyncRequest request) {
        throw new UnsupportedOperationException("TODO: implement syncDocument");
    }
}
