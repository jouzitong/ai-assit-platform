package ai.platform.aiassit.knowledge.manage.mapper;

import ai.platform.aiassit.knowledge.manage.entity.AiKbStoreEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiKbStoreMapper extends CrudMapper<AiKbStoreEntity> {
}
