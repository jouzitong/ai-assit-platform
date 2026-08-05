package ai.platform.aiassit.user.code.controller;

import ai.platform.aiassit.user.api.UserPermissionApi;
import ai.platform.aiassit.user.api.dto.UserPermissionQueryRequest;
import ai.platform.aiassit.user.api.dto.UserPermissionQueryResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 用户权限快照的内部兼容查询接口。
 *
 * <p>当前实现按请求回填用户和账号，并提供平台基础 {@code USER} 角色与 {@code user:read} 权限；后续接入权限存储后应保持该响应契约不变。</p>
 */
@RestController
public class UserPermissionController implements UserPermissionApi {

    /**
     * 查询用户在指定应用中的权限快照。
     *
     * @param request 权限查询请求体，包含用户、账号和可选应用编码
     * @return 用户权限响应，包含规范化应用编码、基础角色和权限代码
     */
    @Override
    public UserPermissionQueryResponse queryPermissions(UserPermissionQueryRequest request) {
        UserPermissionQueryResponse response = new UserPermissionQueryResponse();
        if (request == null) {
            return response;
        }

        response.setUserId(request.getUserId());
        response.setAccount(request.getAccount());
        response.setAppCode(defaultAppCode(request.getAppCode()));
        response.setRoleCodes(Set.of("USER"));
        response.setPermissionCodes(Set.of("user:read"));
        return response;
    }

    private String defaultAppCode(String appCode) {
        if (StringUtils.hasText(appCode)) {
            return appCode;
        }
        return "app-platform-user";
    }
}
