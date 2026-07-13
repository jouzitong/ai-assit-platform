package ai.platform.aiassit.chat.history.convert;

import ai.platform.aiassit.chat.history.entity.AiChatActivityEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatActivityDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiChatActivityConvert extends IConvert<AiChatActivityEntity, AiChatActivityDTO> {
}
