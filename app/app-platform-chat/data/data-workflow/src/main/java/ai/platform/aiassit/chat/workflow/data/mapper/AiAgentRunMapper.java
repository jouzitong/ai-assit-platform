package ai.platform.aiassit.chat.workflow.data.mapper;

import ai.platform.aiassit.chat.workflow.data.entity.AiAgentRunEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiAgentRunMapper extends CrudMapper<AiAgentRunEntity> {
}
