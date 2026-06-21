package ai.platform.aiassist.service.ai.kb.domainservice;

import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassist.service.ai.kb.controller.req.AiKbSyncRequest;
import ai.platform.aiassist.service.ai.kb.controller.resp.AiKbSyncResponse;

import java.util.List;

public interface AiKnowledgeDomainService {

    List<AiKbInfoDTO> kbList(AiKbListRequest request);

    String getKbId(AiKbListRequest request);

    List<AiKbDocumentListItemDTO> listDocuments(AiKbDocumentListRequest request);

    AiKbDocumentDetailDTO getDocumentDetail(String kbCode, String documentCode);

    AiKbDocumentUpsertResponse upsertDocument(AiKbDocumentUpsertRequest request);

    AiKbSyncResponse syncDocument(AiKbSyncRequest request);
}
