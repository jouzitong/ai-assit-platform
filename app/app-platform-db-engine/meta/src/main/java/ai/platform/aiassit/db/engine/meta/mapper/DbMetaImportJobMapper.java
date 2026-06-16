package ai.platform.aiassit.db.engine.meta.mapper;

import ai.platform.aiassit.db.engine.meta.entity.DbMetaImportJobEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface DbMetaImportJobMapper extends CrudMapper<DbMetaImportJobEntity> {
}
