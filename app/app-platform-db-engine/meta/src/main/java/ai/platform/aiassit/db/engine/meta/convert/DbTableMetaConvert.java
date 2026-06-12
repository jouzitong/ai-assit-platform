package ai.platform.aiassit.db.engine.meta.convert;

import ai.platform.aiassit.db.engine.meta.entity.DbTableMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DbTableMetaConvert extends IConvert<DbTableMetaEntity, DbTableMetaDTO> {
}
