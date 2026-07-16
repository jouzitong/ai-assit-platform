package ai.platform.aiassit.chat.workflow.data.mapper;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiChatWorkflowVersionMapper extends CrudMapper<AiChatWorkflowVersionEntity> {
}
