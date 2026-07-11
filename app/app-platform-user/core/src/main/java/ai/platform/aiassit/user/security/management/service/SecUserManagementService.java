package ai.platform.aiassit.user.security.management.service;

import ai.platform.aiassit.user.security.management.entity.dto.SecUserDTO;
import ai.platform.aiassit.user.security.management.entity.dto.SecUserProfileDTO;
import ai.platform.aiassit.user.security.management.entity.dto.SecUserProfileUpdateRequest;
import org.athena.framework.data.jdbc.serivce.IMapperService;

public interface SecUserManagementService extends IMapperService<SecUserDTO> {

    SecUserProfileDTO getProfile(Long userId);

    SecUserProfileDTO updateProfile(Long userId, SecUserProfileUpdateRequest request);
}
