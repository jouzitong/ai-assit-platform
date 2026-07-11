package ai.platform.aiassit.user.security.management.convert;

import ai.platform.aiassit.user.security.management.entity.SecRoleManagementEntity;
import ai.platform.aiassit.user.security.management.entity.dto.SecRoleDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SecRoleManagementConvert extends IConvert<SecRoleManagementEntity, SecRoleDTO> {
}
