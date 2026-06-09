package ai.platform.aiassit.chat.history.mapper;

import ai.platform.aiassit.chat.history.entity.AiChatArtifactEntity;
import org.athena.framework.data.mybatis.mapper.CrudMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiChatArtifactMapper extends CrudMapper<AiChatArtifactEntity> {
}
