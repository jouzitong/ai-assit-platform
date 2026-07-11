package ai.platform.aiassit.user.security.management.convert;

import ai.platform.aiassit.user.security.management.entity.SecUserManagementEntity;
import ai.platform.aiassit.user.security.management.entity.dto.SecUserDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SecUserManagementConvert extends IConvert<SecUserManagementEntity, SecUserDTO> {
}
