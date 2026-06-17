package ai.platform.aiassist.service.ai.kb.service;

import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbDocumentDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

public interface AiKbDocumentService extends IMapperService<AiKbDocumentDTO> {

    AiKbDocumentDTO getByKbCodeAndDocumentCode(String kbCode, String documentCode);
}
