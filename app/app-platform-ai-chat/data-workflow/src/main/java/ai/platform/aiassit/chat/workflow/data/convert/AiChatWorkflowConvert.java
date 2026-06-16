package ai.platform.aiassit.chat.workflow.data.convert;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AiChatWorkflowConvert extends IConvert<AiChatWorkflowEntity, AiChatWorkflowDTO> {

    @Override
    @Mapping(target = "lastModifiedBy", source = "updatedBy")
    AiChatWorkflowDTO toDTO(AiChatWorkflowEntity entity);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    AiChatWorkflowEntity toEntity(AiChatWorkflowDTO dto);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    void editEntityFromDto(AiChatWorkflowDTO dto, @MappingTarget AiChatWorkflowEntity entity);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    void updateEntityFromDto(AiChatWorkflowDTO dto, @MappingTarget AiChatWorkflowEntity entity);
}
