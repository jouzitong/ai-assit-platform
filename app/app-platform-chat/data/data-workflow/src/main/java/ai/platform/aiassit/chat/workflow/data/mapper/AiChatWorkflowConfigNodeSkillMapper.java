package ai.platform.aiassit.chat.workflow.data.mapper;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowConfigNodeSkillEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiChatWorkflowConfigNodeSkillMapper extends CrudMapper<AiChatWorkflowConfigNodeSkillEntity> {
}
