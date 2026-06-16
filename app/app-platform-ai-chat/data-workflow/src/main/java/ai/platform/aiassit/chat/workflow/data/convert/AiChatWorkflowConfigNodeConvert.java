package ai.platform.aiassit.chat.workflow.data.convert;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowConfigNodeEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowConfigNodeDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AiChatWorkflowConfigNodeConvert extends IConvert<AiChatWorkflowConfigNodeEntity, AiChatWorkflowConfigNodeDTO> {

    @Override
    @Mapping(target = "lastModifiedBy", source = "updatedBy")
    AiChatWorkflowConfigNodeDTO toDTO(AiChatWorkflowConfigNodeEntity entity);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    AiChatWorkflowConfigNodeEntity toEntity(AiChatWorkflowConfigNodeDTO dto);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    void editEntityFromDto(AiChatWorkflowConfigNodeDTO dto, @MappingTarget AiChatWorkflowConfigNodeEntity entity);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    void updateEntityFromDto(AiChatWorkflowConfigNodeDTO dto, @MappingTarget AiChatWorkflowConfigNodeEntity entity);
}
