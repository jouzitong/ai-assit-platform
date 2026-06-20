package ai.platform.aiassist.service.ai.kb.domainservice;

import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentListRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassist.service.ai.api.dto.AiKbListRequest;

import java.util.List;

public interface AiKnowledgeBaseQueryDomainService {

    List<AiKbInfoDTO> kbList(AiKbListRequest request);

    List<AiKbDocumentListItemDTO> listDocuments(AiKbDocumentListRequest request);

    AiKbDocumentDetailDTO getDocumentDetail(String kbCode, String documentCode);
}
