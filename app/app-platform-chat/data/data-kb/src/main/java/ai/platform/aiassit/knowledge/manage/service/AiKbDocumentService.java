package ai.platform.aiassit.knowledge.manage.service;

import ai.platform.aiassit.knowledge.manage.entity.document.dto.AiKbDocumentDTO;
import ai.platform.aiassit.knowledge.manage.entity.document.req.AiKbDocumentQueryRequest;
import org.athena.framework.data.jdbc.serivce.IMapperService;

import java.util.List;

public interface AiKbDocumentService extends IMapperService<AiKbDocumentDTO> {

    AiKbDocumentDTO getByKbCodeAndDocumentCode(String kbCode, String documentCode);

    List<AiKbDocumentDTO> listByQuery(AiKbDocumentQueryRequest query);
}
