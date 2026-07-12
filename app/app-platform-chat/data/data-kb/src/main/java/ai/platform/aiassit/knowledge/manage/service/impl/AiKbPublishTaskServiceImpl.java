package ai.platform.aiassit.knowledge.manage.service.impl;

import ai.platform.aiassit.knowledge.manage.convert.AiKbPublishTaskConvert;
import ai.platform.aiassit.knowledge.manage.entity.AiKbPublishTaskEntity;
import ai.platform.aiassit.knowledge.manage.entity.dto.AiKbPublishTaskDTO;
import ai.platform.aiassit.knowledge.manage.entity.req.AiKbPublishTaskQueryRequest;
import ai.platform.aiassit.knowledge.manage.mapper.AiKbPublishTaskMapper;
import ai.platform.aiassit.knowledge.manage.service.AiKbPublishTaskService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    @Override
    public AiKbPublishTaskDTO getByTaskCode(String taskCode) {
        if (!StringUtils.hasText(taskCode)) {
            return null;
        }
        AiKbPublishTaskQueryRequest query = new AiKbPublishTaskQueryRequest();
        query.setTaskCode(taskCode.trim());
        query.setPage(1);
        query.setSize(1);
        return get(query);
    }
}
