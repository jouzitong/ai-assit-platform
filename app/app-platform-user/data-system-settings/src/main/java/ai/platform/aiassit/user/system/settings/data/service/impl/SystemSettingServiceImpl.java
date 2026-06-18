package ai.platform.aiassit.user.system.settings.data.service.impl;

import ai.platform.aiassit.user.system.settings.data.convert.SystemSettingConvert;
import ai.platform.aiassit.user.system.settings.data.entity.SystemSettingEntity;
import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingDTO;
import ai.platform.aiassit.user.system.settings.data.mapper.SystemSettingMapper;
import ai.platform.aiassit.user.system.settings.data.service.SystemSettingService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SystemSettingServiceImpl
        extends BaseMapperService<SystemSettingEntity, SystemSettingMapper, SystemSettingDTO>
        implements SystemSettingService {

    private final SystemSettingConvert systemSettingConvert;

    public SystemSettingServiceImpl(SystemSettingConvert systemSettingConvert) {
        this.systemSettingConvert = systemSettingConvert;
    }

    @Override
    protected IConvert<SystemSettingEntity, SystemSettingDTO> convert() {
        return systemSettingConvert;
    }

    @Override
    public String queryValueByKey(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        SystemSettingEntity entity = baseMapper.selectBySettingKey(key);
        if (entity == null || Boolean.FALSE.equals(entity.getEnabled())) {
            return null;
        }
        return entity.getSettingValue();
    }
}
