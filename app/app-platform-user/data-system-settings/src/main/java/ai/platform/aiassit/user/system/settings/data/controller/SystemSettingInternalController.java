package ai.platform.aiassit.user.system.settings.data.controller;

import ai.platform.aiassit.user.system.settings.api.SystemSettingInternalApi;
import ai.platform.aiassit.user.system.settings.data.service.SystemSettingService;
import org.athena.framework.web.vo.R;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统运行参数的内部只读查询接口。
 *
 * <p>供其他服务按稳定配置键读取最终生效值，避免跨服务直接访问系统参数存储。</p>
 */
@RestController
@RequestMapping("/internal/v1/system-settings")
public class SystemSettingInternalController implements SystemSettingInternalApi {

    private final SystemSettingService service;

    public SystemSettingInternalController(SystemSettingService service) {
        this.service = service;
    }

    /**
     * 根据配置键查询系统参数的当前值。
     *
     * @param key 系统参数配置键
     * @return 包装后的参数值；未配置时由服务层按约定返回空值或默认值
     */
    @Override
    public R<String> queryValueByKey(String key) {
        return R.ok(service.queryValueByKey(key));
    }
}
