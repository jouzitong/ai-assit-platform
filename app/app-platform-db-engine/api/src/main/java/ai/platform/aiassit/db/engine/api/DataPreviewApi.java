package ai.platform.aiassit.db.engine.api;

import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryRequest;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryResponse;
import org.athena.framework.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 面向 Agent Tool 的受控数据预览内部契约。
 *
 * <p>该接口只接受虚拟模型与虚拟字段语义，不接受物理表、SQL、数据源或执行参数。</p>
 */
@FeignClient(
        name = "dbEngine",
        contextId = "platformDataPreviewClient",
        path = "/dbEngine"
)
public interface DataPreviewApi {

    @PostMapping("/internal/v1/data-preview/query")
    R<DataPreviewQueryResponse> query(@RequestBody DataPreviewQueryRequest request);
}
