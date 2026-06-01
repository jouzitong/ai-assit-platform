package ai.platform.aiassit.chat.meta.mapper;

import ai.platform.aiassit.chat.meta.entity.AiModelConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiModelConfigMapper extends CrudMapper<AiModelConfigEntity> {
}
