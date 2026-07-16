package ai.platform.aiassit.chat.workflow.data.controller;

import ai.platform.aiassit.chat.workflow.data.entity.dto.control.AgentControlDTOs;
import ai.platform.aiassit.chat.workflow.data.service.control.AiAgentControlService;
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

@RestController
@RequestMapping("/api/v1/ai/agent-entries")
public class AiAgentEntryBindingController {

    private final AiAgentControlService service;

    public AiAgentEntryBindingController(AiAgentControlService service) {
        this.service = service;
    }

    @GetMapping
    public R<List<AgentControlDTOs.EntrySelection>> entries() {
        return R.ok(service.listEntrySelections());
    }

    @PutMapping("/{entryCode}")
    public R<AgentControlDTOs.EntrySelection> updateEntry(
            @PathVariable String entryCode,
            @Valid @RequestBody AgentControlDTOs.EntrySelectionRequest request) {
        return R.ok(service.updateEntrySelection(entryCode, request));
    }

    @GetMapping("/{entryCode}/bindings")
    public R<List<AgentControlDTOs.EntryBinding>> bindings(@PathVariable String entryCode) {
        return R.ok(service.listEntryBindings(entryCode));
    }

    @GetMapping("/{entryCode}/available-agents")
    public R<List<AgentEntrySummary>> availableAgents(@PathVariable String entryCode) {
        return R.ok(service.listAvailable(entryCode));
    }

    @PutMapping("/{entryCode}/bindings")
    public R<AgentControlDTOs.EntryBinding> bind(
            @PathVariable String entryCode,
            @Valid @RequestBody AgentControlDTOs.EntryBindingRequest request) {
        return R.ok(service.upsertEntryBinding(entryCode, request));
    }

    @DeleteMapping("/bindings/{bindingId}")
    public R<Boolean> delete(@PathVariable Long bindingId) {
        return R.ok(service.deleteEntryBinding(bindingId));
    }
}
