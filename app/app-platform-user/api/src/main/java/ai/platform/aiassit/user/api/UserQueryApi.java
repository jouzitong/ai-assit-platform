package ai.platform.aiassit.user.api;

import ai.platform.aiassit.user.api.dto.UserQueryRequest;
import ai.platform.aiassit.user.api.dto.UserQueryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 用户查询 API（内部服务调用）。
 */
@FeignClient(
        name = "user",
        contextId = "platformUserClient",
        path = "/user"
)
public interface UserQueryApi {

    @PostMapping("/internal/v1/user/query")
    UserQueryResponse queryUser(@RequestBody UserQueryRequest request);
}
