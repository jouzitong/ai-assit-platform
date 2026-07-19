package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.db.engine.api.DataPreviewApi;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryRequest;
import ai.platform.aiassit.db.engine.api.dto.DataPreviewQueryResponse;
import ai.platform.aiassit.db.engine.virtualization.adapter.service.DataPreviewApplicationService;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Agent 数据预览内部入口。 */
@RestController
@RequestMapping
public class DataPreviewController implements DataPreviewApi {

    private final DataPreviewApplicationService service;

    public DataPreviewController(DataPreviewApplicationService service) {
        this.service = service;
    }

    @Override
    @PostMapping("/internal/v1/data-preview/query")
    public R<DataPreviewQueryResponse> query(@RequestBody DataPreviewQueryRequest request) {
        return R.ok(service.query(request));
    }
}
