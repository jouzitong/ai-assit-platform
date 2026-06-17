package ai.platform.aiassist.service.ai.kb.service;

import ai.platform.aiassist.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbStoreDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

import java.util.List;

public interface AiKbStoreService extends IMapperService<AiKbStoreDTO> {

    AiKbStoreDTO getByKbCode(String kbCode);

    List<AiKbStoreDTO> list(AiKbListRequest request);
}
