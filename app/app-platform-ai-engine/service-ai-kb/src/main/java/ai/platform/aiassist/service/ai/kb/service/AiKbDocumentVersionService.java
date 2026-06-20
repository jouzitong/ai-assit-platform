package ai.platform.aiassist.service.ai.kb.service;

import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbDocumentVersionDTO;
import ai.platform.aiassist.service.ai.kb.entity.req.AiKbDocumentVersionQueryRequest;
import org.athena.framework.data.jdbc.serivce.IMapperService;

import java.util.List;

public interface AiKbDocumentVersionService extends IMapperService<AiKbDocumentVersionDTO> {

    List<AiKbDocumentVersionDTO> listByQuery(AiKbDocumentVersionQueryRequest query);
}
