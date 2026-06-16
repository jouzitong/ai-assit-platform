package ai.platform.aiassit.chat.workflow.data.convert;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowConfigNodeSkillEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowConfigNodeSkillDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AiChatWorkflowConfigNodeSkillConvert extends IConvert<AiChatWorkflowConfigNodeSkillEntity, AiChatWorkflowConfigNodeSkillDTO> {

    @Override
    @Mapping(target = "lastModifiedBy", source = "updatedBy")
    AiChatWorkflowConfigNodeSkillDTO toDTO(AiChatWorkflowConfigNodeSkillEntity entity);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    AiChatWorkflowConfigNodeSkillEntity toEntity(AiChatWorkflowConfigNodeSkillDTO dto);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    void editEntityFromDto(AiChatWorkflowConfigNodeSkillDTO dto, @MappingTarget AiChatWorkflowConfigNodeSkillEntity entity);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    void updateEntityFromDto(AiChatWorkflowConfigNodeSkillDTO dto, @MappingTarget AiChatWorkflowConfigNodeSkillEntity entity);
}
