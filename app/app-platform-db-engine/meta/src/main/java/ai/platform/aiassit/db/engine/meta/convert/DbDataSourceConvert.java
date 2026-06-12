package ai.platform.aiassit.db.engine.meta.convert;

import ai.platform.aiassit.db.engine.meta.entity.DbDataSourceEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbDataSourceDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DbDataSourceConvert extends IConvert<DbDataSourceEntity, DbDataSourceDTO> {
}
