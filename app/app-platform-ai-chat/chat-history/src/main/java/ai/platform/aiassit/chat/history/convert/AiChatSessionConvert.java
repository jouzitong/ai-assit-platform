package ai.platform.aiassit.chat.history.convert;

import ai.platform.aiassit.chat.history.entity.AiChatSessionEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface AiChatSessionConvert extends IConvert<AiChatSessionEntity, AiChatSessionDTO> {

    @Override
    AiChatSessionDTO toDTO(AiChatSessionEntity entity);

    @Override
    AiChatSessionEntity toEntity(AiChatSessionDTO dto);

    @Override
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void editEntityFromDto(AiChatSessionDTO dto, @MappingTarget AiChatSessionEntity entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    void updateEntityFromDto(AiChatSessionDTO dto, @MappingTarget AiChatSessionEntity entity);
}
