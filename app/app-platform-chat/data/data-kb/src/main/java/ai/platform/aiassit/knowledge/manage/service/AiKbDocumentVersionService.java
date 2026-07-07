package ai.platform.aiassit.knowledge.manage.service;

import ai.platform.aiassit.knowledge.manage.entity.dto.AiKbDocumentVersionDTO;
import ai.platform.aiassit.knowledge.manage.entity.req.AiKbDocumentVersionQueryRequest;
import org.athena.framework.data.jdbc.serivce.IMapperService;

import java.util.List;

public interface AiKbDocumentVersionService extends IMapperService<AiKbDocumentVersionDTO> {

    List<AiKbDocumentVersionDTO> listByQuery(AiKbDocumentVersionQueryRequest query);
}
