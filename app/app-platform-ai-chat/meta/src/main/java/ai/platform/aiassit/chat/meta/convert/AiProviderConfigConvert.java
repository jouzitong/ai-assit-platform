package ai.platform.aiassit.chat.meta.convert;

import ai.platform.aiassit.chat.meta.entity.AiProviderConfigEntity;
import ai.platform.aiassit.chat.meta.entity.dto.AiProviderConfigDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface AiProviderConfigConvert extends IConvert<AiProviderConfigEntity, AiProviderConfigDTO> {

    @Override
    AiProviderConfigDTO toDTO(AiProviderConfigEntity entity);

    @Override
    AiProviderConfigEntity toEntity(AiProviderConfigDTO dto);

    @Override
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void editEntityFromDto(AiProviderConfigDTO dto, @MappingTarget AiProviderConfigEntity entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    void updateEntityFromDto(AiProviderConfigDTO dto, @MappingTarget AiProviderConfigEntity entity);
}
