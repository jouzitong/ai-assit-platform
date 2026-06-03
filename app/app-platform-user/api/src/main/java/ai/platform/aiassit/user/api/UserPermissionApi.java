package ai.platform.aiassit.user.api;

import ai.platform.aiassit.user.api.dto.UserPermissionQueryRequest;
import ai.platform.aiassit.user.api.dto.UserPermissionQueryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 用户权限查询 API（内部服务调用）。
 */
@FeignClient(
        name = "app-platform-user",
        contextId = "platformUserClient",
        path = "/user"
)
public interface UserPermissionApi {

    @PostMapping("/internal/v1/user/permissions/query")
    UserPermissionQueryResponse queryPermissions(@RequestBody UserPermissionQueryRequest request);
}
