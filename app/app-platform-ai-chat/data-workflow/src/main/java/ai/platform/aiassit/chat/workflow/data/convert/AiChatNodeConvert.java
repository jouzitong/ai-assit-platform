package ai.platform.aiassit.chat.workflow.data.convert;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatNodeEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatNodeDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AiChatNodeConvert extends IConvert<AiChatNodeEntity, AiChatNodeDTO> {

    @Override
    @Mapping(target = "lastModifiedBy", source = "updatedBy")
    AiChatNodeDTO toDTO(AiChatNodeEntity entity);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    AiChatNodeEntity toEntity(AiChatNodeDTO dto);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    void editEntityFromDto(AiChatNodeDTO dto, @MappingTarget AiChatNodeEntity entity);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    @Mapping(target = "deleted", ignore = true)
    void updateEntityFromDto(AiChatNodeDTO dto, @MappingTarget AiChatNodeEntity entity);
}
