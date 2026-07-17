package ai.platform.aiassit.chat.agent.control.data.mapper;

import ai.platform.aiassit.chat.agent.control.data.entity.AiChatSkillPackageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiChatSkillPackageMapper extends CrudMapper<AiChatSkillPackageEntity> {
}
