package ai.platform.aiassit.chat.workflow.data.convert;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatNodeEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatNodeDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiChatNodeConvert extends IConvert<AiChatNodeEntity, AiChatNodeDTO> {

}
