package ai.platform.aiassit.model.mapper;

import ai.platform.aiassit.model.entity.AiClientConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiClientConfigMapper extends CrudMapper<AiClientConfigEntity> {
}
