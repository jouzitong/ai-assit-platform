package ai.platform.aiassit.user.api;

import ai.platform.aiassit.user.api.dto.UserPermissionQueryRequest;
import ai.platform.aiassit.user.api.dto.UserPermissionQueryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 用户权限查询 API（内部服务调用）。
 */
@FeignClient(
        name = "${app.platform-user.name:app-platform-user}",
        url = "${app.platform-user.url:http://127.0.0.1:8082}"
)
@RequestMapping("/api/v1/internal/user")
public interface UserPermissionApi {

    @PostMapping("/permissions/query")
    UserPermissionQueryResponse queryPermissions(@RequestBody UserPermissionQueryRequest request);
}
