package ai.platform.aiassit.user.system.settings.data.convert;

import ai.platform.aiassit.user.system.settings.data.entity.SystemSettingEntity;
import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SystemSettingConvert extends IConvert<SystemSettingEntity, SystemSettingDTO> {

}
