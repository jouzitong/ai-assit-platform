package ai.platform.aiassit.knowledge.manage.service;

import ai.platform.aiassit.knowledge.manage.entity.document.dto.AiKbDocumentContentDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

public interface AiKbDocumentContentService extends IMapperService<AiKbDocumentContentDTO> {

    AiKbDocumentContentDTO getByDocumentId(Long documentId);
}
