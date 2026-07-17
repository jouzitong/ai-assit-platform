package ai.platform.aiassit.chat.agent.control.data.mapper;

import ai.platform.aiassit.chat.agent.control.data.entity.AiAgentEntryBindingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiAgentEntryBindingMapper extends CrudMapper<AiAgentEntryBindingEntity> {
}
