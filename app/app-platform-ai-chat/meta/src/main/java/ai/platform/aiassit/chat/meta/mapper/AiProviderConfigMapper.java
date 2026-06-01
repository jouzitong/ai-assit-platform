package ai.platform.aiassit.chat.meta.mapper;

import ai.platform.aiassit.chat.meta.entity.AiProviderConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiProviderConfigMapper extends CrudMapper<AiProviderConfigEntity> {
}
