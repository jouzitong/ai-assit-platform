package ai.platform.aiassit.user.system.settings.data.controller;

import ai.platform.aiassit.user.system.settings.api.SystemSettingInternalApi;
import ai.platform.aiassit.user.system.settings.data.service.SystemSettingService;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/system-settings")
public class SystemSettingInternalController implements SystemSettingInternalApi {

    private final SystemSettingService service;

    public SystemSettingInternalController(SystemSettingService service) {
        this.service = service;
    }

    @Override
    public R<String> queryValueByKey(String key) {
        return R.ok(service.queryValueByKey(key));
    }
}
