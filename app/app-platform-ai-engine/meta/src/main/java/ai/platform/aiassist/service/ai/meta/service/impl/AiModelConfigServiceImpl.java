package ai.platform.aiassist.service.ai.meta.service.impl;

import ai.platform.aiassist.service.ai.api.dto.AiEnabledModelDTO;
import ai.platform.aiassist.service.ai.meta.convert.AiModelConfigConvert;
import ai.platform.aiassist.service.ai.meta.entity.dto.AiModelConfigDTO;
import ai.platform.aiassist.service.ai.meta.entity.AiModelConfigEntity;
import ai.platform.aiassist.service.ai.meta.mapper.AiModelConfigMapper;
import ai.platform.aiassist.service.ai.meta.entity.req.AiMetaQueryRequest;
import ai.platform.aiassist.service.ai.meta.service.AiModelConfigService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

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
    public List<AiEnabledModelDTO> selectEnabledModels() {
        return baseMapper.selectEnabledModels();
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
