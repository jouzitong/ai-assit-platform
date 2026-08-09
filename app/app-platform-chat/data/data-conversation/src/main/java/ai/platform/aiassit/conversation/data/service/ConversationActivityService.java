package ai.platform.aiassit.conversation.data.service;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationActivityDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

import java.util.Collection;
import java.util.List;

public interface ConversationActivityService extends IMapperService<ConversationActivityDTO> {

    /** Loads owned activities for a bounded set of rounds without scanning the full session. */
    List<ConversationActivityDTO> queryByRoundCodes(
            String sessionCode, Long userId, Collection<String> roundCodes);
}
