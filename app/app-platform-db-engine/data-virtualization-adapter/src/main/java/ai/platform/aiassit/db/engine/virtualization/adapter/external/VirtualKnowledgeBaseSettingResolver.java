package ai.platform.aiassit.db.engine.virtualization.adapter.external;

import ai.platform.aiassit.db.engine.api.constant.DbEngineBizCodeConstant;
import ai.platform.aiassit.db.engine.api.constant.DbEngineSystemSettingKeys;
import ai.platform.aiassit.user.system.settings.api.SystemSettingInternalApi;
import lombok.extern.slf4j.Slf4j;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.web.vo.R;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class VirtualKnowledgeBaseSettingResolver {

    private final SystemSettingInternalApi systemSettingInternalApi;

    public VirtualKnowledgeBaseSettingResolver(SystemSettingInternalApi systemSettingInternalApi) {
        this.systemSettingInternalApi = systemSettingInternalApi;
    }

    public String resolve() {
        R<String> response = systemSettingInternalApi.queryValueByKey(DbEngineSystemSettingKeys.KNOWLEDGE_BASE_CODE);
        if (response == null || response.getCode() != 0 || !StringUtils.hasText(response.getData())) {
            log.warn("virtual knowledge base setting is missing, settingKey={}, responseCode={}, hasData={}",
                    DbEngineSystemSettingKeys.KNOWLEDGE_BASE_CODE,
                    response == null ? null : response.getCode(),
                    response != null && StringUtils.hasText(response.getData()));
            throw BizException.of(DbEngineBizCodeConstant.KB_ID_SETTING_MISSING,
                    DbEngineSystemSettingKeys.KNOWLEDGE_BASE_CODE);
        }
        return response.getData().trim();
    }
}
