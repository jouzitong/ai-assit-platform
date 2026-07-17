package ai.platform.aiassit.conversation.data.mapper;

import ai.platform.aiassit.conversation.data.entity.ConversationArtifactEntity;
import org.athena.framework.data.mybatis.mapper.CrudMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationArtifactMapper extends CrudMapper<ConversationArtifactEntity> {
}
