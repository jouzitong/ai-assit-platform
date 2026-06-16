package ai.platform.aiassit.chat.workflow.data.convert;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatSkillEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatSkillDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AiChatSkillConvert extends IConvert<AiChatSkillEntity, AiChatSkillDTO> {

    @Override
    @Mapping(target = "lastModifiedBy", source = "updatedBy")
    AiChatSkillDTO toDTO(AiChatSkillEntity entity);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    AiChatSkillEntity toEntity(AiChatSkillDTO dto);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    void editEntityFromDto(AiChatSkillDTO dto, @MappingTarget AiChatSkillEntity entity);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    void updateEntityFromDto(AiChatSkillDTO dto, @MappingTarget AiChatSkillEntity entity);
}
