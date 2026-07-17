package ai.platform.aiassit.knowledge.manage.service;

import ai.platform.aiassit.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassit.knowledge.manage.entity.store.dto.AiKbAgentKnowledgeDTO;
import ai.platform.aiassit.knowledge.manage.entity.store.dto.AiKbStoreDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

import java.util.List;

public interface AiKbStoreService extends IMapperService<AiKbStoreDTO> {

    AiKbStoreDTO getByKbCode(String kbCode);

    List<AiKbStoreDTO> list(AiKbListRequest request);

    /** Lists enabled, synchronized KBs as the secret-free catalog available to an Agent run. */
    List<AiKbAgentKnowledgeDTO> availableKnowledgeBases();
}
