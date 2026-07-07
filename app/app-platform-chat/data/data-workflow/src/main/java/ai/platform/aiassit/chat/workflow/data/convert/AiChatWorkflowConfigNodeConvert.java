package ai.platform.aiassit.chat.workflow.data.convert;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowConfigNodeEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowConfigNodeDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiChatWorkflowConfigNodeConvert extends IConvert<AiChatWorkflowConfigNodeEntity, AiChatWorkflowConfigNodeDTO> {

}
