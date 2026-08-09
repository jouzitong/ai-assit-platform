package ai.platform.aiassit.conversation.data.service;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

import java.util.Collection;
import java.util.List;

public interface ConversationMessageService extends IMapperService<ConversationMessageDTO> {

    /** Loads messages for an already ownership-checked set of rounds in storage order. */
    List<ConversationMessageDTO> queryByRoundCodes(Collection<String> roundCodes);

    /** Loads one round without scanning all messages in its session. */
    List<ConversationMessageDTO> queryByRoundCode(String roundCode);
}
