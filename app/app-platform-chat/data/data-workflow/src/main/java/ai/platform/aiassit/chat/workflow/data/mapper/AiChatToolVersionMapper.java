package ai.platform.aiassit.chat.workflow.data.mapper;

import ai.platform.aiassit.chat.workflow.data.entity.AiChatToolVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiChatToolVersionMapper extends CrudMapper<AiChatToolVersionEntity> {
}
