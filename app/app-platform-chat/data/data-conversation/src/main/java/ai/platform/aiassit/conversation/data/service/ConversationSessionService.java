package ai.platform.aiassit.conversation.data.service;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

public interface ConversationSessionService extends IMapperService<ConversationSessionDTO> {

    int updateGroupCode(Long userId, String sessionCode, String groupCode);

    int clearGroupCodeByUserAndGroup(Long userId, String groupCode);
}
