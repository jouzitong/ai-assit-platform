package ai.platform.aiassit.user.system.settings.data.controller;

import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingDTO;
import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingImportResultDTO;
import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingTransferDocument;
import ai.platform.aiassit.user.system.settings.data.entity.req.SystemSettingExportRequest;
import ai.platform.aiassit.user.system.settings.data.entity.req.SystemSettingQueryRequest;
import ai.platform.aiassit.user.system.settings.data.service.SystemSettingService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/system-settings")
public class SystemSettingController
        extends BaseController<SystemSettingDTO, SystemSettingQueryRequest, SystemSettingService> {

    private final SystemSettingService service;

    public SystemSettingController(SystemSettingService service) {
        this.service = service;
    }

    @Override
    protected SystemSettingService service() {
        return service;
    }

    @PostMapping("/import-json")
    public R<SystemSettingImportResultDTO> importJson(@RequestParam("file") MultipartFile file) throws IOException {
        return R.ok(service.importJsonFile(file));
    }

    @PostMapping("/export-json")
    public R<List<SystemSettingTransferDocument>> exportJson(
            @RequestBody(required = false) SystemSettingExportRequest request) {
        return R.ok(service.exportJson(request));
    }
}
