package ai.platform.aiassist.service.ai.meta.service.impl;

import ai.platform.aiassist.service.ai.meta.convert.AiProviderConfigConvert;
import ai.platform.aiassist.service.ai.meta.entity.dto.AiProviderConfigDTO;
import ai.platform.aiassist.service.ai.meta.entity.AiProviderConfigEntity;
import ai.platform.aiassist.service.ai.meta.mapper.AiProviderConfigMapper;
import ai.platform.aiassist.service.ai.meta.entity.req.AiMetaQueryRequest;
import ai.platform.aiassist.service.ai.meta.service.AiProviderConfigService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiProviderConfigServiceImpl
        extends BaseMapperService<AiProviderConfigEntity, AiProviderConfigMapper, AiProviderConfigDTO>
        implements AiProviderConfigService {

    private final AiProviderConfigConvert aiProviderConfigConvert;

    public AiProviderConfigServiceImpl(AiProviderConfigConvert aiProviderConfigConvert) {
        this.aiProviderConfigConvert = aiProviderConfigConvert;
    }

    @Override
    protected IConvert<AiProviderConfigEntity, AiProviderConfigDTO> convert() {
        return aiProviderConfigConvert;
    }

    public AiProviderConfigDTO newDTO() {
        return new AiProviderConfigDTO();
    }

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
