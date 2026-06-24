package ai.platform.aiassit.chat.workflow.data.convert;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowConfigEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowConfigDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiChatWorkflowConfigConvert extends IConvert<AiChatWorkflowConfigEntity, AiChatWorkflowConfigDTO> {

}
