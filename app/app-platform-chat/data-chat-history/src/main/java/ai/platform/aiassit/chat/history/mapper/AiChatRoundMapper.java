package ai.platform.aiassit.chat.history.mapper;

import ai.platform.aiassit.chat.history.entity.AiChatRoundEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiChatRoundMapper extends CrudMapper<AiChatRoundEntity> {
}
