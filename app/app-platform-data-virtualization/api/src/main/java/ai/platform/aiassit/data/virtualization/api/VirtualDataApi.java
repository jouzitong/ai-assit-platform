package ai.platform.aiassit.data.virtualization.api;

import ai.platform.aiassit.data.virtualization.api.dto.VirtualCommandRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCommandResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualExplainResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import org.athena.framework.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** 跨模块使用的虚拟数据执行契约。 */
@FeignClient(
        name = "dbEngine",
        contextId = "platformVirtualDataClient",
        path = "/dbEngine"
)
public interface VirtualDataApi {

    @PostMapping("/internal/v1/virtual-data/query")
    R<VirtualQueryResponse> query(@RequestBody VirtualQueryRequest request);

    @PostMapping("/internal/v1/virtual-data/command")
    R<VirtualCommandResponse> command(@RequestBody VirtualCommandRequest request);

    @PostMapping("/internal/v1/virtual-data/explain")
    R<VirtualExplainResponse> explain(@RequestBody VirtualQueryRequest request);
}
