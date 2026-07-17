package ai.platform.aiassit.chat.agent.control.data.controller;

import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.SkillControlDTOs;
import ai.platform.aiassit.chat.agent.control.data.entity.dto.control.ValidationReportDTO;
import ai.platform.aiassit.chat.agent.control.data.service.control.AiSkillControlService;
import jakarta.validation.Valid;
import org.athena.framework.web.vo.R;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/skills")
public class AiSkillManageController {

    private final AiSkillControlService service;

    public AiSkillManageController(AiSkillControlService service) {
        this.service = service;
    }

    @GetMapping
    public R<List<SkillControlDTOs.Catalog>> catalogs() {
        return R.ok(service.listCatalogs());
    }

    @GetMapping("/{skillCode}")
    public R<SkillControlDTOs.Version> skill(@PathVariable String skillCode) {
        return R.ok(service.getSkill(skillCode));
    }

    @PostMapping("/form")
    public R<SkillControlDTOs.Version> createForm(
            @Valid @RequestBody SkillControlDTOs.FormDraftRequest request) {
        return R.ok(service.createFormDraft(request));
    }

    @PutMapping("/{skillCode}")
    public R<SkillControlDTOs.Version> update(@PathVariable String skillCode,
                                              @Valid @RequestBody SkillControlDTOs.UpdateRequest request) {
        return R.ok(service.updateSkill(skillCode, request));
    }

    @DeleteMapping("/{skillCode}")
    public R<Boolean> delete(@PathVariable String skillCode) {
        return R.ok(service.deleteSkill(skillCode));
    }

    @PostMapping(value = "/packages/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<SkillControlDTOs.Inspection> inspect(@RequestParam("file") MultipartFile file) {
        return R.ok(service.inspect(file));
    }

    @PostMapping("/packages/{draftId}/import")
    public R<SkillControlDTOs.Version> importZip(@PathVariable String draftId,
                                                 @RequestBody(required = false) SkillControlDTOs.ImportRequest request) {
        return R.ok(service.importDraft(draftId, request == null ? new SkillControlDTOs.ImportRequest() : request));
    }

    @GetMapping("/{skillCode}/versions")
    public R<List<SkillControlDTOs.Version>> versions(@PathVariable String skillCode) {
        return R.ok(service.listVersions(skillCode));
    }

    @GetMapping("/{skillCode}/versions/{version}")
    public R<SkillControlDTOs.Version> version(@PathVariable String skillCode,
                                               @PathVariable Integer version) {
        return R.ok(service.getVersion(skillCode, version));
    }

    @GetMapping("/{skillCode}/versions/{version}/files")
    public R<List<SkillControlDTOs.FileItem>> files(@PathVariable String skillCode,
                                                    @PathVariable Integer version) {
        return R.ok(service.listVersionFiles(skillCode, version));
    }

    @GetMapping("/{skillCode}/versions/{version}/package")
    public ResponseEntity<byte[]> downloadPackage(@PathVariable String skillCode,
                                                   @PathVariable Integer version) {
        AiSkillControlService.PackageDownload value = service.getVersionPackage(skillCode, version);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(value.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Package-SHA256", value.checksum())
                .body(value.content());
    }

    @PostMapping("/{skillCode}/versions/{version}/validate")
    public R<ValidationReportDTO> validate(@PathVariable String skillCode,
                                           @PathVariable Integer version) {
        return R.ok(service.validateVersion(skillCode, version));
    }

    @PostMapping("/{skillCode}/versions/{version}/publish")
    public R<SkillControlDTOs.Version> publish(@PathVariable String skillCode,
                                               @PathVariable Integer version) {
        return R.ok(service.publishVersion(skillCode, version));
    }
}
