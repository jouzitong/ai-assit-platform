package ai.platform.aiassit.user.system.settings.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "user",
        contextId = "platformUserSystemSettingsClient",
        path = "/user/internal/v1/system-settings"
)
public interface SystemSettingInternalApi {

    @GetMapping("/value")
    String queryValueByKey(@RequestParam("key") String key);
}
