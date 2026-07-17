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

@RestController
@RequestMapping("/api/v1/ai/agents")
public class AiAgentManageController {

    private final AiAgentControlService service;

    public AiAgentManageController(AiAgentControlService service) {
        this.service = service;
    }

    @GetMapping
    public R<List<AgentControlDTOs.Catalog>> list() {
        return R.ok(service.listAgents());
    }

    @GetMapping("/{agentCode}")
    public R<AgentControlDTOs.Version> get(@PathVariable String agentCode) {
        return R.ok(service.getAgent(agentCode));
    }

    @PostMapping
    public R<AgentControlDTOs.Version> create(@Valid @RequestBody AgentControlDTOs.CreateRequest request) {
        return R.ok(service.createAgent(request));
    }

    @PutMapping("/{agentCode}")
    public R<AgentControlDTOs.Version> update(@PathVariable String agentCode,
                                              @Valid @RequestBody AgentControlDTOs.UpdateRequest request) {
        return R.ok(service.updateAgent(agentCode, request));
    }

    @DeleteMapping("/{agentCode}")
    public R<Boolean> delete(@PathVariable String agentCode) {
        return R.ok(service.deleteAgent(agentCode));
    }

    @PostMapping("/{agentCode}/versions")
    public R<AgentControlDTOs.Version> createVersion(
            @PathVariable String agentCode,
            @Valid @RequestBody AgentControlDTOs.VersionCreateRequest request) {
        return R.ok(service.createVersion(agentCode, request));
    }

    @GetMapping("/{agentCode}/versions")
    public R<List<AgentControlDTOs.Version>> versions(@PathVariable String agentCode) {
        return R.ok(service.listVersions(agentCode));
    }

    @GetMapping("/{agentCode}/versions/{version}")
    public R<AgentControlDTOs.Version> version(@PathVariable String agentCode,
                                               @PathVariable Integer version) {
        return R.ok(service.getVersion(agentCode, version));
    }

    @PostMapping("/{agentCode}/versions/{version}/validate")
    public R<ValidationReportDTO> validate(@PathVariable String agentCode,
                                           @PathVariable Integer version) {
        return R.ok(service.validateVersion(agentCode, version));
    }

    @GetMapping("/{agentCode}/versions/{version}/compatibility")
    public R<ValidationReportDTO> compatibility(@PathVariable String agentCode,
                                                @PathVariable Integer version) {
        return R.ok(service.compatibility(agentCode, version));
    }

    @PostMapping("/{agentCode}/versions/{version}/test-runs")
    public R<Map<String, Object>> test(@PathVariable String agentCode,
                                       @PathVariable Integer version,
                                       @RequestBody(required = false) Map<String, Object> input) {
        return R.ok(service.testVersion(agentCode, version, input));
    }

    @PostMapping("/{agentCode}/versions/{version}/publish")
    public R<AgentControlDTOs.Version> publish(@PathVariable String agentCode,
                                               @PathVariable Integer version) {
        return R.ok(service.publishVersion(agentCode, version));
    }
}
