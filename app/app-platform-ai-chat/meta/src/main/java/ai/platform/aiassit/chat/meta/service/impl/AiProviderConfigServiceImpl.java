package ai.platform.aiassit.chat.meta.service.impl;

import ai.platform.aiassit.chat.meta.entity.dto.AiProviderConfigDTO;
import ai.platform.aiassit.chat.meta.entity.AiProviderConfigEntity;
import ai.platform.aiassit.chat.meta.mapper.AiProviderConfigMapper;
import ai.platform.aiassit.chat.meta.entity.req.AiMetaQueryRequest;
import ai.platform.aiassit.chat.meta.service.AiProviderConfigService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiProviderConfigServiceImpl
        extends BaseMapperService<AiProviderConfigEntity, AiProviderConfigMapper, AiProviderConfigDTO>
        implements AiProviderConfigService {

    @Override
    public AiProviderConfigDTO newDTO() {
        return new AiProviderConfigDTO();
    }

    @Override
    public AiProviderConfigEntity newEntity() {
        return new AiProviderConfigEntity();
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<AiProviderConfigEntity> buildQuery(Query query) {
        QueryWrapper<AiProviderConfigEntity> wrapper = super.buildQuery(query);
        if (query instanceof AiMetaQueryRequest req) {
            if (StringUtils.hasText(req.getProviderCode())) {
                wrapper.lambda().eq(AiProviderConfigEntity::getProviderCode, req.getProviderCode());
            }
            if (req.getEnabled() != null) {
                wrapper.lambda().eq(AiProviderConfigEntity::getEnabled, req.getEnabled());
            }
        }
        return wrapper;
    }
}
