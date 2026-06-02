package ai.platform.aiassit.chat.meta.convert;

import ai.platform.aiassit.chat.meta.entity.AiModelCredentialEntity;
import ai.platform.aiassit.chat.meta.entity.dto.AiModelCredentialDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface AiModelCredentialConvert extends IConvert<AiModelCredentialEntity, AiModelCredentialDTO> {

    @Override
    AiModelCredentialDTO toDTO(AiModelCredentialEntity entity);

    @Override
    AiModelCredentialEntity toEntity(AiModelCredentialDTO dto);

    @Override
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void editEntityFromDto(AiModelCredentialDTO dto, @MappingTarget AiModelCredentialEntity entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    void updateEntityFromDto(AiModelCredentialDTO dto, @MappingTarget AiModelCredentialEntity entity);
}
