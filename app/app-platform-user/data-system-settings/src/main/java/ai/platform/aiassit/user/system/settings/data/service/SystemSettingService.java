package ai.platform.aiassit.user.system.settings.data.service;

import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

public interface SystemSettingService extends IMapperService<SystemSettingDTO> {

    String queryValueByKey(String key);
}
