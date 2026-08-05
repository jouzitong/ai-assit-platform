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

/**
 * DB Engine 对外暴露的虚拟数据执行内部入口。
 *
 * <p>调用方只能提交虚拟实体和字段语义；查询、命令和执行计划解释均通过虚拟层路由、权限和物理数据源适配器完成。</p>
 */
@RestController
public class VirtualDataExecutionController implements VirtualDataApi {

    private final VirtualQueryGateway queryGateway;
    private final VirtualCommandGateway commandGateway;

    public VirtualDataExecutionController(VirtualQueryGateway queryGateway, VirtualCommandGateway commandGateway) {
        this.queryGateway = queryGateway;
        this.commandGateway = commandGateway;
    }

    /**
     * 在虚拟数据模型上执行受控读取查询。
     *
     * @param request 虚拟查询请求体，包含实体、字段、条件和返回限制
     * @return 包装后的查询结果，包含受策略约束的数据和执行信息
     */
    @Override
    @PostMapping("/internal/v1/virtual-data/query")
    public R<VirtualQueryResponse> query(@RequestBody VirtualQueryRequest request) {
        return R.ok(queryGateway.query(request));
    }

    /**
     * 在虚拟数据模型上执行受控命令操作。
     *
     * @param request 虚拟命令请求体，包含目标实体、操作意图和业务数据
     * @return 包装后的命令执行结果，包含影响范围和执行状态
     */
    @Override
    @PostMapping("/internal/v1/virtual-data/command")
    public R<VirtualCommandResponse> command(@RequestBody VirtualCommandRequest request) {
        return R.ok(commandGateway.command(request));
    }

    /**
     * 解释虚拟查询将如何被路由和执行，但不读取业务数据。
     *
     * @param request 虚拟查询请求体，包含待分析的实体、字段和条件
     * @return 包装后的执行计划说明，包含路由、字段处理和策略信息
     */
    @Override
    @PostMapping("/internal/v1/virtual-data/explain")
    public R<VirtualExplainResponse> explain(@RequestBody VirtualQueryRequest request) {
        return R.ok(queryGateway.explain(request));
    }
}
