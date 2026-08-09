package ai.platform.aiassit.conversation.data.mapper;

import ai.platform.aiassit.conversation.data.entity.ConversationMemorySessionPolicyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface ConversationMemorySessionPolicyMapper extends CrudMapper<ConversationMemorySessionPolicyEntity> {
}
