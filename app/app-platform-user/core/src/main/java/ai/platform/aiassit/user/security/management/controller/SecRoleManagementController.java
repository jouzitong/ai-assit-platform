package ai.platform.aiassit.user.security.management.controller;

import ai.platform.aiassit.user.security.management.entity.dto.SecRoleDTO;
import ai.platform.aiassit.user.security.management.entity.req.SecRoleQueryRequest;
import ai.platform.aiassit.user.security.management.service.SecRoleManagementService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security/roles")
public class SecRoleManagementController
        extends BaseController<SecRoleDTO, SecRoleQueryRequest, SecRoleManagementService> {

    private final SecRoleManagementService service;

    public SecRoleManagementController(SecRoleManagementService service) {
        this.service = service;
    }

    @Override
    protected SecRoleManagementService service() {
        return service;
    }
}
