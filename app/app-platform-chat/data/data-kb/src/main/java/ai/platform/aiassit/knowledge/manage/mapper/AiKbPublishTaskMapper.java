package ai.platform.aiassit.knowledge.manage.mapper;

import ai.platform.aiassit.knowledge.manage.entity.task.AiKbPublishTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiKbPublishTaskMapper extends CrudMapper<AiKbPublishTaskEntity> {
}
