package ai.platform.aiassit.conversation.data.service;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

import java.util.List;

public interface ConversationRoundService extends IMapperService<ConversationRoundDTO> {

    /** Returns at most {@code limit} newest owned rounds, ordered oldest to newest. */
    List<ConversationRoundDTO> queryRecent(String sessionCode, Long userId, int limit);

    /** Returns rounds older than the supplied storage cursor, ordered oldest to newest. */
    List<ConversationRoundDTO> queryBefore(String sessionCode, Long userId, Long beforeId, int limit);

    /** Returns rounds newer than the supplied storage cursor, ordered oldest to newest. */
    List<ConversationRoundDTO> queryAfter(String sessionCode, Long userId, Long afterId, int limit);

    /** Returns the newest owned round without loading the full session. */
    ConversationRoundDTO queryLatest(String sessionCode, Long userId);

    /** Loads one round while enforcing session and user ownership in the SQL predicate. */
    ConversationRoundDTO queryOwned(String roundCode, String sessionCode, Long userId);
}
