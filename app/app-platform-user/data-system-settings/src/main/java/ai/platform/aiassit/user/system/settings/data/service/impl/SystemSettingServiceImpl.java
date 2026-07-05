package ai.platform.aiassit.user.system.settings.data.service.impl;

import ai.platform.aiassit.user.system.settings.data.convert.SystemSettingConvert;
import ai.platform.aiassit.user.system.settings.data.entity.SystemSettingEntity;
import ai.platform.aiassit.user.system.settings.data.entity.dto.SystemSettingDTO;
import ai.platform.aiassit.user.system.settings.data.entity.req.SystemSettingQueryRequest;
import ai.platform.aiassit.user.system.settings.data.mapper.SystemSettingMapper;
import ai.platform.aiassit.user.system.settings.data.service.SystemSettingService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
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
    protected <Query extends BaseRequest> QueryWrapper<SystemSettingEntity> buildQuery(Query query) {
        QueryWrapper<SystemSettingEntity> qw = super.buildQuery(query);
        if (query instanceof SystemSettingQueryRequest request) {
            if (StringUtils.hasText(request.getKeyword())) {
                String keyword = request.getKeyword();
                qw.like("setting_key", keyword)
                        .or()
                        .like("setting_value", keyword)
                        .or()
                        .like("description", keyword);
            }
        }


        return qw;
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
