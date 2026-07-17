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

@RestController
@RequestMapping("/api/v1/ai/tools")
public class AiToolManageController {

    private final AiToolControlService service;

    public AiToolManageController(AiToolControlService service) {
        this.service = service;
    }

    @GetMapping
    public R<List<ToolControlDTOs.Catalog>> catalogs() {
        return R.ok(service.listCatalogs());
    }

    @GetMapping("/{toolCode}")
    public R<ToolControlDTOs.Version> tool(@PathVariable String toolCode) {
        return R.ok(service.getTool(toolCode));
    }

    @PostMapping
    public R<ToolControlDTOs.Version> create(@Valid @RequestBody ToolControlDTOs.DraftRequest request) {
        return R.ok(service.createDraft(request));
    }

    @PutMapping("/{toolCode}")
    public R<ToolControlDTOs.Version> update(@PathVariable String toolCode,
                                             @Valid @RequestBody ToolControlDTOs.DraftRequest request) {
        return R.ok(service.updateTool(toolCode, request));
    }

    @DeleteMapping("/{toolCode}")
    public R<Boolean> delete(@PathVariable String toolCode) {
        return R.ok(service.deleteTool(toolCode));
    }

    @PostMapping("/{toolCode}/versions")
    public R<ToolControlDTOs.Version> createVersion(@PathVariable String toolCode,
                                                    @Valid @RequestBody ToolControlDTOs.DraftRequest request) {
        return R.ok(service.createVersion(toolCode, request));
    }

    @GetMapping("/{toolCode}/versions")
    public R<List<ToolControlDTOs.Version>> versions(@PathVariable String toolCode) {
        return R.ok(service.listVersions(toolCode));
    }

    @GetMapping("/{toolCode}/versions/{version}")
    public R<ToolControlDTOs.Version> version(@PathVariable String toolCode,
                                              @PathVariable Integer version) {
        return R.ok(service.getVersion(toolCode, version));
    }

    @PostMapping("/{toolCode}/versions/{version}/validate")
    public R<ValidationReportDTO> validate(@PathVariable String toolCode,
                                           @PathVariable Integer version) {
        return R.ok(service.validateVersion(toolCode, version));
    }

    @PostMapping("/{toolCode}/versions/{version}/publish")
    public R<ToolControlDTOs.Version> publish(@PathVariable String toolCode,
                                              @PathVariable Integer version) {
        return R.ok(service.publishVersion(toolCode, version));
    }

    @PostMapping("/{toolCode}/versions/{version}/test-runs")
    public R<Map<String, Object>> test(@PathVariable String toolCode,
                                       @PathVariable Integer version,
                                       @RequestBody(required = false) Map<String, Object> payload) {
        return R.ok(service.testVersion(toolCode, version, payload == null ? Map.of() : payload));
    }
}
