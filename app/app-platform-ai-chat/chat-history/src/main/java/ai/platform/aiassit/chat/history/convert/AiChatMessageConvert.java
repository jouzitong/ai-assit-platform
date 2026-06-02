package ai.platform.aiassit.chat.history.convert;

import ai.platform.aiassit.chat.history.entity.AiChatMessageEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface AiChatMessageConvert extends IConvert<AiChatMessageEntity, AiChatMessageDTO> {

    @Override
    AiChatMessageDTO toDTO(AiChatMessageEntity entity);

    @Override
    AiChatMessageEntity toEntity(AiChatMessageDTO dto);

    @Override
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void editEntityFromDto(AiChatMessageDTO dto, @MappingTarget AiChatMessageEntity entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    void updateEntityFromDto(AiChatMessageDTO dto, @MappingTarget AiChatMessageEntity entity);
}
