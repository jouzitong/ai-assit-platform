package ai.platform.aiassist.service.ai.kb.service.impl;

import ai.platform.aiassist.service.ai.kb.convert.AiKbPublishTaskConvert;
import ai.platform.aiassist.service.ai.kb.entity.AiKbPublishTaskEntity;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbPublishTaskDTO;
import ai.platform.aiassist.service.ai.kb.mapper.AiKbPublishTaskMapper;
import ai.platform.aiassist.service.ai.kb.service.AiKbPublishTaskService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiKbPublishTaskServiceImpl
        extends BaseMapperService<AiKbPublishTaskEntity, AiKbPublishTaskMapper, AiKbPublishTaskDTO>
        implements AiKbPublishTaskService {

    private final AiKbPublishTaskConvert convert;

    public AiKbPublishTaskServiceImpl(AiKbPublishTaskConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiKbPublishTaskEntity, AiKbPublishTaskDTO> convert() {
        return convert;
    }

    public AiKbPublishTaskDTO newDTO() {
        return new AiKbPublishTaskDTO();
    }

    public AiKbPublishTaskEntity newEntity() {
        return new AiKbPublishTaskEntity();
    }
}
