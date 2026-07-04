package ai.platform.aiassit.chat.history.convert;

import ai.platform.aiassit.chat.history.entity.AiChatMessageEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiChatMessageConvert extends IConvert<AiChatMessageEntity, AiChatMessageDTO> {

}
