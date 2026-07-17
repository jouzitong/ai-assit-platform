package ai.platform.aiassit.conversation.data.convert;

import ai.platform.aiassit.conversation.data.entity.ConversationRoundEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConversationRoundConvert extends IConvert<ConversationRoundEntity, ConversationRoundDTO> {

}
