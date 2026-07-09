package ai.platform.aiassit.user.api;

import ai.platform.aiassit.user.api.dto.ErrCodeQueryRequest;
import ai.platform.aiassit.user.api.dto.ErrCodeQueryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 错误码查询 API（内部服务调用）。
 */
@FeignClient(
        name = "user",
        contextId = "platformErrCodeClient",
        path = "/user"
)
public interface ErrCodeQueryApi {

    @PostMapping("/internal/v1/err-code/query")
    ErrCodeQueryResponse queryErrCode(@RequestBody ErrCodeQueryRequest request);
}
