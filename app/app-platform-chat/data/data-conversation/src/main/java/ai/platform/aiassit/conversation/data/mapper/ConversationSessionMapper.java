package ai.platform.aiassit.conversation.data.mapper;

import ai.platform.aiassit.conversation.data.entity.ConversationSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface ConversationSessionMapper extends CrudMapper<ConversationSessionEntity> {
}
