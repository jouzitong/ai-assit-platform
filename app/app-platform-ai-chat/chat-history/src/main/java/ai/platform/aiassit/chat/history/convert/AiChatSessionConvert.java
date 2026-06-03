package ai.platform.aiassit.chat.history.convert;

import ai.platform.aiassit.chat.history.entity.AiChatSessionEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiChatSessionConvert extends IConvert<AiChatSessionEntity, AiChatSessionDTO> {

}
