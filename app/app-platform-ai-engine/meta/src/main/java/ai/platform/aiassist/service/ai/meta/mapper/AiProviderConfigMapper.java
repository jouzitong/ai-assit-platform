package ai.platform.aiassist.service.ai.meta.mapper;

import ai.platform.aiassist.service.ai.meta.entity.AiProviderConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiProviderConfigMapper extends CrudMapper<AiProviderConfigEntity> {
}
