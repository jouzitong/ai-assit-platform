package ai.platform.aiassit.db.engine.meta.mapper;

import ai.platform.aiassit.db.engine.meta.entity.DbTableMetaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface DbTableMetaMapper extends CrudMapper<DbTableMetaEntity> {
}
