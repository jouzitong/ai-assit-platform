package ai.platform.aiassit.conversation.data.mapper;

import ai.platform.aiassit.conversation.data.entity.ConversationGroupEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

/** 分组基础 CRUD Mapper。 */
@Mapper
public interface ConversationGroupMapper extends CrudMapper<ConversationGroupEntity> {
}
