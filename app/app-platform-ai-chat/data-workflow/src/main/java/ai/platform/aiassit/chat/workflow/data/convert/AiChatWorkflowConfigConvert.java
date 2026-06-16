package ai.platform.aiassit.chat.workflow.data.convert;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowConfigEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowConfigDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AiChatWorkflowConfigConvert extends IConvert<AiChatWorkflowConfigEntity, AiChatWorkflowConfigDTO> {

    @Override
    @Mapping(target = "lastModifiedBy", source = "updatedBy")
    AiChatWorkflowConfigDTO toDTO(AiChatWorkflowConfigEntity entity);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    AiChatWorkflowConfigEntity toEntity(AiChatWorkflowConfigDTO dto);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    void editEntityFromDto(AiChatWorkflowConfigDTO dto, @MappingTarget AiChatWorkflowConfigEntity entity);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    void updateEntityFromDto(AiChatWorkflowConfigDTO dto, @MappingTarget AiChatWorkflowConfigEntity entity);
}
