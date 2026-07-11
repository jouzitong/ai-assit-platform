package ai.platform.aiassit.user.security.management.convert;

import ai.platform.aiassit.user.security.management.entity.SecUserRoleManagementEntity;
import ai.platform.aiassit.user.security.management.entity.dto.SecUserRoleDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SecUserRoleManagementConvert extends IConvert<SecUserRoleManagementEntity, SecUserRoleDTO> {
}
