package ai.platform.aiassit.conversation.data.convert;

import ai.platform.aiassit.conversation.data.entity.ConversationActivityEntity;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationActivityDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConversationActivityConvert extends IConvert<ConversationActivityEntity, ConversationActivityDTO> {
}
