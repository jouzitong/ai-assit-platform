package ai.platform.aiassit.chat.workflow.data.convert;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowConfigNodeSkillEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowConfigNodeSkillDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiChatWorkflowConfigNodeSkillConvert extends IConvert<AiChatWorkflowConfigNodeSkillEntity, AiChatWorkflowConfigNodeSkillDTO> {

}
