package ai.platform.aiassit.conversation.data.mapper;

import ai.platform.aiassit.conversation.data.entity.ConversationMemorySyncTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface ConversationMemorySyncTaskMapper extends CrudMapper<ConversationMemorySyncTaskEntity> {
}
