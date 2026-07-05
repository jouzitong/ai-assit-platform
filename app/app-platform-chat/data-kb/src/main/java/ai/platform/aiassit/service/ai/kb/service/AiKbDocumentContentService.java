package ai.platform.aiassit.service.ai.kb.service;

import ai.platform.aiassit.service.ai.kb.entity.dto.AiKbDocumentContentDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

public interface AiKbDocumentContentService extends IMapperService<AiKbDocumentContentDTO> {

    AiKbDocumentContentDTO getByDocumentId(Long documentId);
}
