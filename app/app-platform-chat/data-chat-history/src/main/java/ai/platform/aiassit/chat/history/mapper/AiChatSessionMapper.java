package ai.platform.aiassit.chat.history.mapper;

import ai.platform.aiassit.chat.history.entity.AiChatSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiChatSessionMapper extends CrudMapper<AiChatSessionEntity> {
}
