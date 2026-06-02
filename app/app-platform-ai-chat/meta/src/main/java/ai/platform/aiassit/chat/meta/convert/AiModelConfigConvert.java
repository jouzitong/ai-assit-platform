package ai.platform.aiassit.chat.meta.convert;

import ai.platform.aiassit.chat.meta.entity.AiModelConfigEntity;
import ai.platform.aiassit.chat.meta.entity.dto.AiModelConfigDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface AiModelConfigConvert extends IConvert<AiModelConfigEntity, AiModelConfigDTO> {

    @Override
    AiModelConfigDTO toDTO(AiModelConfigEntity entity);

    @Override
    AiModelConfigEntity toEntity(AiModelConfigDTO dto);

    @Override
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void editEntityFromDto(AiModelConfigDTO dto, @MappingTarget AiModelConfigEntity entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    void updateEntityFromDto(AiModelConfigDTO dto, @MappingTarget AiModelConfigEntity entity);
}
