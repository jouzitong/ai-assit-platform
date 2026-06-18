package ai.platform.aiassit.user.system.settings.data.convert;

import ai.platform.aiassit.user.system.settings.data.entity.SystemSettingEntity;
import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SystemSettingConvert extends IConvert<SystemSettingEntity, SystemSettingDTO> {

    @Override
    @Mapping(target = "lastModifiedBy", source = "updatedBy")
    SystemSettingDTO toDTO(SystemSettingEntity entity);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    SystemSettingEntity toEntity(SystemSettingDTO dto);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    void editEntityFromDto(SystemSettingDTO dto, @MappingTarget SystemSettingEntity entity);

    @Override
    @Mapping(target = "updatedBy", source = "lastModifiedBy")
    void updateEntityFromDto(SystemSettingDTO dto, @MappingTarget SystemSettingEntity entity);
}
