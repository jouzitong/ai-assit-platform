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
        name = "${app.platform-user.name:app-platform-user}",
        url = "${app.platform-user.url:http://127.0.0.1:8082/user}"
)
public interface UserPermissionApi {

    @PostMapping("/api/v1/internal/user/permissions/query")
    UserPermissionQueryResponse queryPermissions(@RequestBody UserPermissionQueryRequest request);
}
