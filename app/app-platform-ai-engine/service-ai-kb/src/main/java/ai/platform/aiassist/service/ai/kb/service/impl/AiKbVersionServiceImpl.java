package ai.platform.aiassist.service.ai.kb.service.impl;

import ai.platform.aiassist.service.ai.kb.convert.AiKbVersionConvert;
import ai.platform.aiassist.service.ai.kb.entity.AiKbVersionEntity;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbVersionDTO;
import ai.platform.aiassist.service.ai.kb.mapper.AiKbVersionMapper;
import ai.platform.aiassist.service.ai.kb.service.AiKbVersionService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiKbVersionServiceImpl
        extends BaseMapperService<AiKbVersionEntity, AiKbVersionMapper, AiKbVersionDTO>
        implements AiKbVersionService {

    private final AiKbVersionConvert convert;

    public AiKbVersionServiceImpl(AiKbVersionConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiKbVersionEntity, AiKbVersionDTO> convert() {
        return convert;
    }

    public AiKbVersionDTO newDTO() {
        return new AiKbVersionDTO();
    }

    public AiKbVersionEntity newEntity() {
        return new AiKbVersionEntity();
    }
}
