package ai.platform.aiassit.chat.agent.control.data.controller;

import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.AgentControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.agent.control.data.service.control.AiAgentControlService;
import jakarta.validation.Valid;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Agent 定义、版本与发布生命周期管理接口。
 *
 * <p>负责维护 Agent 的草稿、版本校验、兼容性检查和发布状态；运行时只应消费已发布且校验通过的版本。</p>
 */
@RestController
@RequestMapping("/api/v1/ai/agents")
public class AiAgentManageController {

    private final AiAgentControlService service;

    public AiAgentManageController(AiAgentControlService service) {
        this.service = service;
    }

    /**
     * 查询 Agent 目录。
     *
     * @return Agent 基本信息及当前可用版本摘要
     */
    @GetMapping
    public R<List<AgentControlDTOs.Catalog>> list() {
        return R.ok(service.listAgents());
    }

    /**
     * 查询指定 Agent 的当前定义。
     *
     * @param agentCode Agent 业务编码
     * @return Agent 当前版本的完整配置
     */
    @GetMapping("/{agentCode}")
    public R<AgentControlDTOs.Version> get(@PathVariable String agentCode) {
        return R.ok(service.getAgent(agentCode));
    }

    /**
     * 创建一个新的 Agent 及其首个可编辑版本。
     *
     * @param request Agent 创建请求体，包含标识、基础信息和初始运行配置
     * @return 新建 Agent 的版本详情
     */
    @PostMapping
    public R<AgentControlDTOs.Version> create(@Valid @RequestBody AgentControlDTOs.CreateRequest request) {
        return R.ok(service.createAgent(request));
    }

    /**
     * 修改指定 Agent 的当前可编辑配置。
     *
     * @param agentCode Agent 业务编码
     * @param request    更新请求体，包含需要调整的 Agent 元数据和运行配置
     * @return 修改后的版本详情
     */
    @PutMapping("/{agentCode}")
    public R<AgentControlDTOs.Version> update(@PathVariable String agentCode,
                                              @Valid @RequestBody AgentControlDTOs.UpdateRequest request) {
        return R.ok(service.updateAgent(agentCode, request));
    }

    /**
     * 删除指定 Agent 及其可删除的版本配置。
     *
     * @param agentCode Agent 业务编码
     * @return 是否成功删除
     */
    @DeleteMapping("/{agentCode}")
    public R<Boolean> delete(@PathVariable String agentCode) {
        return R.ok(service.deleteAgent(agentCode));
    }

    /**
     * 基于指定 Agent 创建一个新的版本草稿。
     *
     * @param agentCode Agent 业务编码
     * @param request    新版本请求体，包含版本说明及待保存的配置
     * @return 新创建的版本详情
     */
    @PostMapping("/{agentCode}/versions")
    public R<AgentControlDTOs.Version> createVersion(
            @PathVariable String agentCode,
            @Valid @RequestBody AgentControlDTOs.VersionCreateRequest request) {
        return R.ok(service.createVersion(agentCode, request));
    }

    /**
     * 查询指定 Agent 的全部历史版本。
     *
     * @param agentCode Agent 业务编码
     * @return 版本列表及各版本状态
     */
    @GetMapping("/{agentCode}/versions")
    public R<List<AgentControlDTOs.Version>> versions(@PathVariable String agentCode) {
        return R.ok(service.listVersions(agentCode));
    }

    /**
     * 查询 Agent 的某个确定版本。
     *
     * @param agentCode Agent 业务编码
     * @param version   版本号
     * @return 该版本的完整定义和发布状态
     */
    @GetMapping("/{agentCode}/versions/{version}")
    public R<AgentControlDTOs.Version> version(@PathVariable String agentCode,
                                               @PathVariable Integer version) {
        return R.ok(service.getVersion(agentCode, version));
    }

    /**
     * 校验 Agent 版本的结构、依赖和运行前置条件。
     *
     * @param agentCode Agent 业务编码
     * @param version   待校验版本号
     * @return 校验报告，包含阻塞问题与修复提示
     */
    @PostMapping("/{agentCode}/versions/{version}/validate")
    public R<ValidationReportDTO> validate(@PathVariable String agentCode,
                                           @PathVariable Integer version) {
        return R.ok(service.validateVersion(agentCode, version));
    }

    /**
     * 检查 Agent 版本与当前平台能力、已配置资源的兼容性。
     *
     * @param agentCode Agent 业务编码
     * @param version   待检查版本号
     * @return 兼容性报告，指出当前环境是否可运行该版本
     */
    @GetMapping("/{agentCode}/versions/{version}/compatibility")
    public R<ValidationReportDTO> compatibility(@PathVariable String agentCode,
                                                @PathVariable Integer version) {
        return R.ok(service.compatibility(agentCode, version));
    }

    /**
     * 使用可选测试输入试运行指定 Agent 版本。
     *
     * @param agentCode Agent 业务编码
     * @param version   待试运行版本号
     * @param input     可选测试请求体，作为本次试运行的业务输入
     * @return 试运行结果、执行输出及诊断信息
     */
    @PostMapping("/{agentCode}/versions/{version}/test-runs")
    public R<Map<String, Object>> test(@PathVariable String agentCode,
                                       @PathVariable Integer version,
                                       @RequestBody(required = false) Map<String, Object> input) {
        return R.ok(service.testVersion(agentCode, version, input));
    }

    /**
     * 发布校验通过的 Agent 版本供业务入口和运行时使用。
     *
     * @param agentCode Agent 业务编码
     * @param version   要发布的版本号
     * @return 发布后的版本详情与状态
     */
    @PostMapping("/{agentCode}/versions/{version}/publish")
    public R<AgentControlDTOs.Version> publish(@PathVariable String agentCode,
                                               @PathVariable Integer version) {
        return R.ok(service.publishVersion(agentCode, version));
    }
}
