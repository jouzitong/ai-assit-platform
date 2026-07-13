package ai.platform.aiassit.data.virtualization.core.controller;

import ai.platform.aiassit.data.virtualization.api.VirtualDataApi;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCommandRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCommandResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualExplainResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import ai.platform.aiassit.data.virtualization.core.execution.VirtualDataCommandService;
import ai.platform.aiassit.data.virtualization.core.execution.VirtualDataQueryService;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VirtualDataExecutionController implements VirtualDataApi {
    private final VirtualDataQueryService queryService;
    private final VirtualDataCommandService commandService;

    public VirtualDataExecutionController(VirtualDataQueryService queryService, VirtualDataCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Override
    @PostMapping("/internal/v1/virtual-data/query")
    public R<VirtualQueryResponse> query(@RequestBody VirtualQueryRequest request) {
        return R.ok(queryService.query(request));
    }

    @Override
    @PostMapping("/internal/v1/virtual-data/command")
    public R<VirtualCommandResponse> command(@RequestBody VirtualCommandRequest request) {
        return R.ok(commandService.command(request));
    }

    @Override
    @PostMapping("/internal/v1/virtual-data/explain")
    public R<VirtualExplainResponse> explain(@RequestBody VirtualQueryRequest request) {
        return R.ok(queryService.explain(request));
    }
}
