package ai.platform.aiassist.service.ai.meta.mapper;

import ai.platform.aiassist.service.ai.meta.entity.AiModelConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiModelConfigMapper extends CrudMapper<AiModelConfigEntity> {
}
