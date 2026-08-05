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

/**
 * 平台安全用户与用户资料管理接口。
 *
 * <p>复用 {@link BaseController} 维护用户基础账号信息；资料接口单独读写展示名等扩展资料，避免将用户管理页面和认证会话结构耦合。</p>
 */
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

    /**
     * 查询指定用户的扩展资料。
     *
     * @param id 用户主键
     * @return 用户资料，包含展示相关的可编辑属性
     */
    @GetMapping("/{id}/profile")
    public SecUserProfileDTO getProfile(@PathVariable Long id) {
        return service.getProfile(id);
    }

    /**
     * 更新指定用户的扩展资料。
     *
     * @param id      用户主键
     * @param request 资料更新请求体，包含允许修改的展示和个人资料字段
     * @return 更新后的用户资料
     */
    @PutMapping("/{id}/profile")
    public SecUserProfileDTO updateProfile(@PathVariable Long id,
                                           @RequestBody SecUserProfileUpdateRequest request) {
        return service.updateProfile(id, request);
    }
}
