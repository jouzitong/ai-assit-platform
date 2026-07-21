package ai.platform.aiassit.conversation.data.service;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationArtifactDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

import java.util.Collection;
import java.util.List;

public interface ConversationArtifactService extends IMapperService<ConversationArtifactDTO> {

    List<ConversationArtifactDTO> queryByRoundCodes(Collection<String> roundCodes);
}
