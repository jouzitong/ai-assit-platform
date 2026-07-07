package ai.platform.aiassit.chat.history.convert;

import ai.platform.aiassit.chat.history.entity.AiChatRoundEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiChatRoundConvert extends IConvert<AiChatRoundEntity, AiChatRoundDTO> {

}
