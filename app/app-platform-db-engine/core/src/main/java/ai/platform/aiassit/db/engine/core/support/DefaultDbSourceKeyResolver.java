package ai.platform.aiassit.db.engine.core.support;

import ai.platform.aiassit.user.system.settings.api.SystemSettingInternalApi;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.web.vo.R;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DefaultDbSourceKeyResolver {

    public static final String DEFAULT_SOURCE_KEY_SETTING_KEY = "dbEngine.access.default.sourceKey";

    private final SystemSettingInternalApi systemSettingInternalApi;

    public DefaultDbSourceKeyResolver(SystemSettingInternalApi systemSettingInternalApi) {
        this.systemSettingInternalApi = systemSettingInternalApi;
    }

    public String resolve(String sourceKey) {
        if (StringUtils.hasText(sourceKey)) {
            return sourceKey.trim();
        }
        R<String> response = systemSettingInternalApi.queryValueByKey(DEFAULT_SOURCE_KEY_SETTING_KEY);
        if (response == null || response.getCode() != 0 || !StringUtils.hasText(response.getData())) {
            throw BizException.of();
        }
        return response.getData().trim();
    }
}
