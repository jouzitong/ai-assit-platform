package ai.platform.aiassit.chat.history.convert;

import ai.platform.aiassit.chat.history.entity.AiChatRoundEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface AiChatRoundConvert extends IConvert<AiChatRoundEntity, AiChatRoundDTO> {

    @Override
    AiChatRoundDTO toDTO(AiChatRoundEntity entity);

    @Override
    AiChatRoundEntity toEntity(AiChatRoundDTO dto);

    @Override
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void editEntityFromDto(AiChatRoundDTO dto, @MappingTarget AiChatRoundEntity entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    void updateEntityFromDto(AiChatRoundDTO dto, @MappingTarget AiChatRoundEntity entity);
}
