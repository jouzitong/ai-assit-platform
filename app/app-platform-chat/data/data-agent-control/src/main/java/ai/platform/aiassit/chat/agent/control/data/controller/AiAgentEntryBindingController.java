package ai.platform.aiassit.chat.agent.control.data.controller;

import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.AgentControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.service.control.AiAgentControlService;
import ai.platform.aiassit.service.ai.spi.agent.AgentEntrySummary;
import jakarta.validation.Valid;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 业务入口与绑定关系管理接口。
 *
 * <p>维护业务入口可选 Agent、绑定顺序及启用状态，使运行时能够按入口解析可执行的 Agent 集合。</p>
 */
@RestController
@RequestMapping("/api/v1/ai/agent-entries")
public class AiAgentEntryBindingController {

    private final AiAgentControlService service;

    public AiAgentEntryBindingController(AiAgentControlService service) {
        this.service = service;
    }

    /**
     * 查询全部 Agent 业务入口及其当前选择状态。
     *
     * @return 可供配置的入口列表，包含入口标识、展示信息和已选 Agent 摘要
     */
    @GetMapping
    public R<List<AgentControlDTOs.EntrySelection>> entries() {
        return R.ok(service.listEntrySelections());
    }

    /**
     * 更新指定业务入口的 Agent 选择策略。
     *
     * @param entryCode 需要配置的业务入口编码
     * @param request    入口选择请求体，包含启用状态及默认 Agent 等配置
     * @return 更新后的入口选择结果
     */
    @PutMapping("/{entryCode}")
    public R<AgentControlDTOs.EntrySelection> updateEntry(
            @PathVariable String entryCode,
            @Valid @RequestBody AgentControlDTOs.EntrySelectionRequest request) {
        return R.ok(service.updateEntrySelection(entryCode, request));
    }

    /**
     * 查询业务入口下已生效的 Agent 绑定关系。
     *
     * @param entryCode 业务入口编码
     * @return 入口绑定列表，包含目标 Agent、优先级和启用状态
     */
    @GetMapping("/{entryCode}/bindings")
    public R<List<AgentControlDTOs.EntryBinding>> bindings(@PathVariable String entryCode) {
        return R.ok(service.listEntryBindings(entryCode));
    }

    /**
     * 查询可绑定到指定入口的候选 Agent。
     *
     * @param entryCode 业务入口编码，用于按入口能力过滤候选 Agent
     * @return 可绑定的 Agent 摘要列表
     */
    @GetMapping("/{entryCode}/available-agents")
    public R<List<AgentEntrySummary>> availableAgents(@PathVariable String entryCode) {
        return R.ok(service.listAvailable(entryCode));
    }

    /**
     * 新增或更新业务入口与 Agent 的绑定关系。
     *
     * @param entryCode 业务入口编码
     * @param request    绑定请求体，描述目标 Agent、优先级及是否启用
     * @return 保存后的入口绑定记录
     */
    @PutMapping("/{entryCode}/bindings")
    public R<AgentControlDTOs.EntryBinding> bind(
            @PathVariable String entryCode,
            @Valid @RequestBody AgentControlDTOs.EntryBindingRequest request) {
        return R.ok(service.upsertEntryBinding(entryCode, request));
    }

    /**
     * 删除一条业务入口与 Agent 的绑定关系。
     *
     * @param bindingId 要删除的绑定记录主键
     * @return 是否成功删除该绑定
     */
    @DeleteMapping("/bindings/{bindingId}")
    public R<Boolean> delete(@PathVariable Long bindingId) {
        return R.ok(service.deleteEntryBinding(bindingId));
    }
}
