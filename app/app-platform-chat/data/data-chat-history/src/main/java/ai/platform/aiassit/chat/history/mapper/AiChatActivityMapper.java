package ai.platform.aiassit.chat.history.mapper;

import ai.platform.aiassit.chat.history.entity.AiChatActivityEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiChatActivityMapper extends CrudMapper<AiChatActivityEntity> {
}
