package ai.platform.aiassit.chat.workflow.data.convert;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatSkillEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatSkillDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiChatSkillConvert extends IConvert<AiChatSkillEntity, AiChatSkillDTO> {

}
