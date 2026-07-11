package ai.platform.aiassit.user.security.management.controller;

import ai.platform.aiassit.user.security.management.entity.dto.SecUserRoleDTO;
import ai.platform.aiassit.user.security.management.entity.req.SecUserRoleQueryRequest;
import ai.platform.aiassit.user.security.management.service.SecUserRoleManagementService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security/user-roles")
public class SecUserRoleManagementController
        extends BaseController<SecUserRoleDTO, SecUserRoleQueryRequest, SecUserRoleManagementService> {

    private final SecUserRoleManagementService service;

    public SecUserRoleManagementController(SecUserRoleManagementService service) {
        this.service = service;
    }

    @Override
    protected SecUserRoleManagementService service() {
        return service;
    }
}
