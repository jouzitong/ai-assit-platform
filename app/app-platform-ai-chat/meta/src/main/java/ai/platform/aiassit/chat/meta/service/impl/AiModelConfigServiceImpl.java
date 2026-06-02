package ai.platform.aiassit.chat.meta.service.impl;

import ai.platform.aiassit.chat.meta.convert.AiModelConfigConvert;
import ai.platform.aiassit.chat.meta.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.chat.meta.entity.AiModelConfigEntity;
import ai.platform.aiassit.chat.meta.mapper.AiModelConfigMapper;
import ai.platform.aiassit.chat.meta.entity.req.AiMetaQueryRequest;
import ai.platform.aiassit.chat.meta.service.AiModelConfigService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiModelConfigServiceImpl
        extends BaseMapperService<AiModelConfigEntity, AiModelConfigMapper, AiModelConfigDTO>
        implements AiModelConfigService {

    private final AiModelConfigConvert aiModelConfigConvert;

    public AiModelConfigServiceImpl(AiModelConfigConvert aiModelConfigConvert) {
        this.aiModelConfigConvert = aiModelConfigConvert;
    }

    @Override
    protected IConvert<AiModelConfigEntity, AiModelConfigDTO> convert() {
        return aiModelConfigConvert;
    }

    public AiModelConfigDTO newDTO() {
        return new AiModelConfigDTO();
    }

    public AiModelConfigEntity newEntity() {
        return new AiModelConfigEntity();
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<AiModelConfigEntity> buildQuery(Query query) {
        QueryWrapper<AiModelConfigEntity> wrapper = super.buildQuery(query);
        if (query instanceof AiMetaQueryRequest req) {
            if (StringUtils.hasText(req.getProviderCode())) {
                wrapper.lambda().eq(AiModelConfigEntity::getProviderCode, req.getProviderCode());
            }
            if (StringUtils.hasText(req.getModelCode())) {
                wrapper.lambda().eq(AiModelConfigEntity::getModelCode, req.getModelCode());
            }
            if (req.getEnabled() != null) {
                wrapper.lambda().eq(AiModelConfigEntity::getEnabled, req.getEnabled());
            }
            wrapper.lambda().orderByAsc(AiModelConfigEntity::getPriority);
        }
        return wrapper;
    }
}
