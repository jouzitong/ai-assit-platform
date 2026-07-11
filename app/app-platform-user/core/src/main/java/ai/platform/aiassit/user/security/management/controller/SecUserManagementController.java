package ai.platform.aiassit.user.security.management.controller;

import ai.platform.aiassit.user.security.management.entity.dto.SecUserDTO;
import ai.platform.aiassit.user.security.management.entity.dto.SecUserProfileDTO;
import ai.platform.aiassit.user.security.management.entity.dto.SecUserProfileUpdateRequest;
import ai.platform.aiassit.user.security.management.entity.req.SecUserQueryRequest;
import ai.platform.aiassit.user.security.management.service.SecUserManagementService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/security/users")
public class SecUserManagementController
        extends BaseController<SecUserDTO, SecUserQueryRequest, SecUserManagementService> {

    private final SecUserManagementService service;

    public SecUserManagementController(SecUserManagementService service) {
        this.service = service;
    }

    @Override
    protected SecUserManagementService service() {
        return service;
    }

    @GetMapping("/{id}/profile")
    public SecUserProfileDTO getProfile(@PathVariable Long id) {
        return service.getProfile(id);
    }

    @PutMapping("/{id}/profile")
    public SecUserProfileDTO updateProfile(@PathVariable Long id,
                                           @RequestBody SecUserProfileUpdateRequest request) {
        return service.updateProfile(id, request);
    }
}
