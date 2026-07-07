package ai.platform.aiassit.chat.workflow.data.convert;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiChatWorkflowConvert extends IConvert<AiChatWorkflowEntity, AiChatWorkflowDTO> {

}
