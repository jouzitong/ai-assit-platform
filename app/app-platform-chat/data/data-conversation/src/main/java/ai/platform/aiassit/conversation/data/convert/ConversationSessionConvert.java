package ai.platform.aiassit.conversation.data.convert;

import ai.platform.aiassit.conversation.data.entity.ConversationSessionEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConversationSessionConvert extends IConvert<ConversationSessionEntity, ConversationSessionDTO> {

}
