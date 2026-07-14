package ai.platform.aiassit.db.engine.virtualization.adapter.controller;

import ai.platform.aiassit.data.virtualization.api.VirtualCommandGateway;
import ai.platform.aiassit.data.virtualization.api.VirtualDataApi;
import ai.platform.aiassit.data.virtualization.api.VirtualQueryGateway;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCommandRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualCommandResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualExplainResponse;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryRequest;
import ai.platform.aiassit.data.virtualization.api.dto.VirtualQueryResponse;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** DB Engine 对外暴露的虚拟数据执行入口。 */
@RestController
public class VirtualDataExecutionController implements VirtualDataApi {

    private final VirtualQueryGateway queryGateway;
    private final VirtualCommandGateway commandGateway;

    public VirtualDataExecutionController(VirtualQueryGateway queryGateway, VirtualCommandGateway commandGateway) {
        this.queryGateway = queryGateway;
        this.commandGateway = commandGateway;
    }

    @Override
    @PostMapping("/internal/v1/virtual-data/query")
    public R<VirtualQueryResponse> query(@RequestBody VirtualQueryRequest request) {
        return R.ok(queryGateway.query(request));
    }

    @Override
    @PostMapping("/internal/v1/virtual-data/command")
    public R<VirtualCommandResponse> command(@RequestBody VirtualCommandRequest request) {
        return R.ok(commandGateway.command(request));
    }

    @Override
    @PostMapping("/internal/v1/virtual-data/explain")
    public R<VirtualExplainResponse> explain(@RequestBody VirtualQueryRequest request) {
        return R.ok(queryGateway.explain(request));
    }
}
