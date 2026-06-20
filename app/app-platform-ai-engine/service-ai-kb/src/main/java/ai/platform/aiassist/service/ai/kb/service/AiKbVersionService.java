package ai.platform.aiassist.service.ai.kb.service;

import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbVersionDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

public interface AiKbVersionService extends IMapperService<AiKbVersionDTO> {

    AiKbVersionDTO getDraftVersion(String kbCode);

    Integer getMaxVersionNo(String kbCode);
}
