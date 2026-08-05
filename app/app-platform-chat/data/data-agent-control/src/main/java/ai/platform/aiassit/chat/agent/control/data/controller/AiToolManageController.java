package ai.platform.aiassit.chat.agent.control.data.controller;

import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ToolControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.agent.control.data.service.control.AiToolControlService;
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
 * Agent 工具定义、版本和运行验证管理接口。
 *
 * <p>维护可被 Agent 调用的工具契约与实现配置，并通过校验、试运行和发布控制工具进入运行时目录。</p>
 */
@RestController
@RequestMapping("/api/v1/ai/tools")
public class AiToolManageController {

    private final AiToolControlService service;

    public AiToolManageController(AiToolControlService service) {
        this.service = service;
    }

    /**
     * 查询可管理的工具目录。
     *
     * @return 工具基础信息及当前版本摘要
     */
    @GetMapping
    public R<List<ToolControlDTOs.Catalog>> catalogs() {
        return R.ok(service.listCatalogs());
    }

    /**
     * 查询指定工具的当前配置。
     *
     * @param toolCode 工具业务编码
     * @return 工具当前版本的完整定义
     */
    @GetMapping("/{toolCode}")
    public R<ToolControlDTOs.Version> tool(@PathVariable String toolCode) {
        return R.ok(service.getTool(toolCode));
    }

    /**
     * 创建一个新的工具草稿。
     *
     * @param request 工具草稿请求体，包含输入输出契约和调用配置
     * @return 创建后的工具版本详情
     */
    @PostMapping
    public R<ToolControlDTOs.Version> create(@Valid @RequestBody ToolControlDTOs.DraftRequest request) {
        return R.ok(service.createDraft(request));
    }

    /**
     * 更新工具的可编辑草稿。
     *
     * @param toolCode 工具业务编码
     * @param request  工具草稿请求体，包含新的契约或执行配置
     * @return 更新后的工具版本详情
     */
    @PutMapping("/{toolCode}")
    public R<ToolControlDTOs.Version> update(@PathVariable String toolCode,
                                             @Valid @RequestBody ToolControlDTOs.DraftRequest request) {
        return R.ok(service.updateTool(toolCode, request));
    }

    /**
     * 删除指定工具及可删除的版本记录。
     *
     * @param toolCode 工具业务编码
     * @return 是否成功删除
     */
    @DeleteMapping("/{toolCode}")
    public R<Boolean> delete(@PathVariable String toolCode) {
        return R.ok(service.deleteTool(toolCode));
    }

    /**
     * 基于指定工具创建新的版本草稿。
     *
     * @param toolCode 工具业务编码
     * @param request  新版本请求体，包含待保存的工具定义
     * @return 新创建的工具版本详情
     */
    @PostMapping("/{toolCode}/versions")
    public R<ToolControlDTOs.Version> createVersion(@PathVariable String toolCode,
                                                    @Valid @RequestBody ToolControlDTOs.DraftRequest request) {
        return R.ok(service.createVersion(toolCode, request));
    }

    /**
     * 查询工具的全部版本。
     *
     * @param toolCode 工具业务编码
     * @return 版本列表及其发布状态
     */
    @GetMapping("/{toolCode}/versions")
    public R<List<ToolControlDTOs.Version>> versions(@PathVariable String toolCode) {
        return R.ok(service.listVersions(toolCode));
    }

    /**
     * 查询工具的指定版本。
     *
     * @param toolCode 工具业务编码
     * @param version  版本号
     * @return 该版本的完整工具定义
     */
    @GetMapping("/{toolCode}/versions/{version}")
    public R<ToolControlDTOs.Version> version(@PathVariable String toolCode,
                                              @PathVariable Integer version) {
        return R.ok(service.getVersion(toolCode, version));
    }

    /**
     * 校验工具版本的参数契约、依赖资源和调用配置。
     *
     * @param toolCode 工具业务编码
     * @param version  待校验版本号
     * @return 校验报告，包含阻塞问题和修复建议
     */
    @PostMapping("/{toolCode}/versions/{version}/validate")
    public R<ValidationReportDTO> validate(@PathVariable String toolCode,
                                           @PathVariable Integer version) {
        return R.ok(service.validateVersion(toolCode, version));
    }

    /**
     * 发布校验通过的工具版本，供 Agent 运行时调用。
     *
     * @param toolCode 工具业务编码
     * @param version  要发布的版本号
     * @return 发布后的工具版本详情
     */
    @PostMapping("/{toolCode}/versions/{version}/publish")
    public R<ToolControlDTOs.Version> publish(@PathVariable String toolCode,
                                              @PathVariable Integer version) {
        return R.ok(service.publishVersion(toolCode, version));
    }

    /**
     * 使用可选测试载荷试运行工具版本。
     *
     * @param toolCode 工具业务编码
     * @param version  待试运行版本号
     * @param payload  可选请求体，作为工具调用的测试参数
     * @return 调用结果、工具输出及失败诊断信息
     */
    @PostMapping("/{toolCode}/versions/{version}/test-runs")
    public R<Map<String, Object>> test(@PathVariable String toolCode,
                                       @PathVariable Integer version,
                                       @RequestBody(required = false) Map<String, Object> payload) {
        return R.ok(service.testVersion(toolCode, version, payload == null ? Map.of() : payload));
    }
}
