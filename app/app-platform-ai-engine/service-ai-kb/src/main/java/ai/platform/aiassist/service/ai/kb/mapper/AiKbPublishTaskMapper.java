package ai.platform.aiassist.service.ai.kb.mapper;

import ai.platform.aiassist.service.ai.kb.entity.AiKbPublishTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiKbPublishTaskMapper extends CrudMapper<AiKbPublishTaskEntity> {
}
