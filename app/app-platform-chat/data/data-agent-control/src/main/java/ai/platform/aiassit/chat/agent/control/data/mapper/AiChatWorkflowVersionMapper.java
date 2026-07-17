package ai.platform.aiassit.chat.agent.control.data.mapper;

import ai.platform.aiassit.chat.agent.control.data.entity.AiChatWorkflowVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiChatWorkflowVersionMapper extends CrudMapper<AiChatWorkflowVersionEntity> {
}
