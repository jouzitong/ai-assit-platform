package ai.platform.aiassist.service.ai.kb.service;

import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbDocumentContentDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

public interface AiKbDocumentContentService extends IMapperService<AiKbDocumentContentDTO> {

    AiKbDocumentContentDTO getByDocumentId(Long documentId);
}
