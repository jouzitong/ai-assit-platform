package ai.platform.aiassit.chat.workflow.data.convert;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatToolEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatToolDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiChatToolConvert extends IConvert<AiChatToolEntity, AiChatToolDTO> {

}
