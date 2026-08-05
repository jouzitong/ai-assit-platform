package ai.platform.aiassit.user.security.management.controller;

import ai.platform.aiassit.user.security.management.entity.dto.SecRoleDTO;
import ai.platform.aiassit.user.security.management.entity.req.SecRoleQueryRequest;
import ai.platform.aiassit.user.security.management.service.SecRoleManagementService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台安全角色的通用 CRUD 接口。
 *
 * <p>复用 {@link BaseController} 维护角色编码、名称和状态；请求体使用 {@link SecRoleDTO}，查询条件使用
 * {@link SecRoleQueryRequest}，角色可被用户角色关系和权限快照引用。</p>
 */
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
