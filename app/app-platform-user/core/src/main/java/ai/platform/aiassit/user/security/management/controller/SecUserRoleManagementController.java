package ai.platform.aiassit.user.security.management.controller;

import ai.platform.aiassit.user.security.management.entity.dto.SecUserRoleDTO;
import ai.platform.aiassit.user.security.management.entity.req.SecUserRoleQueryRequest;
import ai.platform.aiassit.user.security.management.service.SecUserRoleManagementService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户与安全角色绑定关系的通用 CRUD 接口。
 *
 * <p>复用 {@link BaseController} 维护用户、角色和有效状态之间的关系；请求体使用 {@link SecUserRoleDTO}，查询条件使用
 * {@link SecUserRoleQueryRequest}，变更会影响后续权限快照计算。</p>
 */
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
