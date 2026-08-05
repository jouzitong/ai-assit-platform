package ai.platform.aiassit.chat.agent.control.data.controller;

import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.WorkflowControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.service.control.AiWorkflowControlService;
import jakarta.validation.Valid;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 产物工作流的定义、版本和发布管理接口。
 *
 * <p>用于编排 Agent 生成页面、图表等业务产物的执行步骤，并在发布前校验工作流依赖和可执行性。</p>
 */
@RestController
@RequestMapping("/api/v1/ai/workflows")
public class AiArtifactWorkflowManageController {

    private final AiWorkflowControlService service;

    public AiArtifactWorkflowManageController(AiWorkflowControlService service) {
        this.service = service;
    }

    /**
     * 查询全部工作流目录。
     *
     * @return 工作流基础信息和当前版本摘要
     */
    @GetMapping
    public R<List<WorkflowControlDTOs.Catalog>> catalogs() {
        return R.ok(service.listCatalogs());
    }

    /**
     * 查询指定工作流的当前定义。
     *
     * @param workflowCode 工作流业务编码
     * @return 当前工作流版本及其步骤配置
     */
    @GetMapping("/{workflowCode}")
    public R<WorkflowControlDTOs.Version> workflow(@PathVariable String workflowCode) {
        return R.ok(service.getWorkflow(workflowCode));
    }

    /**
     * 创建新的工作流草稿。
     *
     * @param request 工作流草稿请求体，包含流程标识、步骤和产物输出配置
     * @return 创建后的工作流版本
     */
    @PostMapping
    public R<WorkflowControlDTOs.Version> create(
            @Valid @RequestBody WorkflowControlDTOs.DraftRequest request) {
        return R.ok(service.createDraft(request));
    }

    /**
     * 更新指定工作流的可编辑草稿。
     *
     * @param workflowCode 工作流业务编码
     * @param request      工作流草稿请求体，描述新的步骤与产物配置
     * @return 更新后的工作流版本
     */
    @PutMapping("/{workflowCode}")
    public R<WorkflowControlDTOs.Version> update(@PathVariable String workflowCode,
                                                 @Valid @RequestBody WorkflowControlDTOs.DraftRequest request) {
        return R.ok(service.updateWorkflow(workflowCode, request));
    }

    /**
     * 删除指定工作流及可删除的版本记录。
     *
     * @param workflowCode 工作流业务编码
     * @return 是否成功删除
     */
    @DeleteMapping("/{workflowCode}")
    public R<Boolean> delete(@PathVariable String workflowCode) {
        return R.ok(service.deleteWorkflow(workflowCode));
    }

    /**
     * 基于当前工作流创建一个新的版本草稿。
     *
     * @param workflowCode 工作流业务编码
     * @param request      新版本请求体，包含待保存的流程定义
     * @return 新建版本的详情
     */
    @PostMapping("/{workflowCode}/versions")
    public R<WorkflowControlDTOs.Version> createVersion(@PathVariable String workflowCode,
                                                        @Valid @RequestBody WorkflowControlDTOs.DraftRequest request) {
        return R.ok(service.createVersion(workflowCode, request));
    }

    /**
     * 查询工作流的全部版本。
     *
     * @param workflowCode 工作流业务编码
     * @return 工作流版本列表及状态
     */
    @GetMapping("/{workflowCode}/versions")
    public R<List<WorkflowControlDTOs.Version>> versions(@PathVariable String workflowCode) {
        return R.ok(service.listVersions(workflowCode));
    }

    /**
     * 查询工作流的指定版本配置。
     *
     * @param workflowCode 工作流业务编码
     * @param version      版本号
     * @return 该版本的完整工作流定义
     */
    @GetMapping("/{workflowCode}/versions/{version}")
    public R<WorkflowControlDTOs.Version> version(@PathVariable String workflowCode,
                                                  @PathVariable Integer version) {
        return R.ok(service.getVersion(workflowCode, version));
    }

    /**
     * 校验工作流步骤、依赖资源与产物契约。
     *
     * @param workflowCode 工作流业务编码
     * @param version      待校验版本号
     * @return 校验报告，包含阻塞问题和诊断信息
     */
    @PostMapping("/{workflowCode}/versions/{version}/validate")
    public R<ValidationReportDTO> validate(@PathVariable String workflowCode,
                                           @PathVariable Integer version) {
        return R.ok(service.validateVersion(workflowCode, version));
    }

    /**
     * 发布校验通过的工作流版本，使其可被 Agent 运行时引用。
     *
     * @param workflowCode 工作流业务编码
     * @param version      要发布的版本号
     * @return 发布后的工作流版本详情
     */
    @PostMapping("/{workflowCode}/versions/{version}/publish")
    public R<WorkflowControlDTOs.Version> publish(@PathVariable String workflowCode,
                                                  @PathVariable Integer version) {
        return R.ok(service.publishVersion(workflowCode, version));
    }

    /**
     * 使用测试载荷执行工作流版本的试运行。
     *
     * @param workflowCode 工作流业务编码
     * @param version      待试运行版本号
     * @param payload      可选请求体，作为测试步骤的运行输入
     * @return 试运行的步骤输出、产物结果及错误信息
     */
    @PostMapping("/{workflowCode}/versions/{version}/test-runs")
    public R<Map<String, Object>> test(@PathVariable String workflowCode,
                                       @PathVariable Integer version,
                                       @RequestBody(required = false) Map<String, Object> payload) {
        return R.ok(service.testVersion(workflowCode, version, payload == null ? Map.of() : payload));
    }
}
