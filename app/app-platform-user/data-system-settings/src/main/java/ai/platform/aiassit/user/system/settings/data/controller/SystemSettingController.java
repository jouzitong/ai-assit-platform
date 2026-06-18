package ai.platform.aiassit.user.system.settings.data.controller;

import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingDTO;
import ai.platform.aiassit.user.system.settings.data.entity.req.SystemSettingQueryRequest;
import ai.platform.aiassit.user.system.settings.data.service.SystemSettingService;
import org.athena.framework.data.jdbc.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
