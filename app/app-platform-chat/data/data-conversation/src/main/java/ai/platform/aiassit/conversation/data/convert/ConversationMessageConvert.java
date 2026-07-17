package ai.platform.aiassit.conversation.data.convert;

import ai.platform.aiassit.conversation.data.entity.ConversationMessageEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationMessageDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConversationMessageConvert extends IConvert<ConversationMessageEntity, ConversationMessageDTO> {

}
