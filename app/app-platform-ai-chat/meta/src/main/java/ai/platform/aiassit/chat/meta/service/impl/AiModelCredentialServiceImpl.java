package ai.platform.aiassit.chat.meta.service.impl;

import ai.platform.aiassit.chat.meta.entity.dto.AiModelCredentialDTO;
import ai.platform.aiassit.chat.meta.entity.AiModelCredentialEntity;
import ai.platform.aiassit.chat.meta.mapper.AiModelCredentialMapper;
import ai.platform.aiassit.chat.meta.entity.req.AiMetaQueryRequest;
import ai.platform.aiassit.chat.meta.service.AiModelCredentialService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiModelCredentialServiceImpl
        extends BaseMapperService<AiModelCredentialEntity, AiModelCredentialMapper, AiModelCredentialDTO>
        implements AiModelCredentialService {

    @Override
    public AiModelCredentialDTO newDTO() {
        return new AiModelCredentialDTO();
    }

    @Override
    public AiModelCredentialEntity newEntity() {
        return new AiModelCredentialEntity();
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<AiModelCredentialEntity> buildQuery(Query query) {
        QueryWrapper<AiModelCredentialEntity> wrapper = super.buildQuery(query);
        if (query instanceof AiMetaQueryRequest req) {
            if (StringUtils.hasText(req.getProviderCode())) {
                wrapper.lambda().eq(AiModelCredentialEntity::getProviderCode, req.getProviderCode());
            }
            if (StringUtils.hasText(req.getModelCode())) {
                wrapper.lambda().eq(AiModelCredentialEntity::getModelCode, req.getModelCode());
            }
            if (req.getEnabled() != null) {
                wrapper.lambda().eq(AiModelCredentialEntity::getEnabled, req.getEnabled());
            }
        }
        return wrapper;
    }
}
