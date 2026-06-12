package ai.platform.aiassit.db.engine.meta.convert;

import ai.platform.aiassit.db.engine.meta.entity.DbTableFieldMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DbTableFieldMetaConvert extends IConvert<DbTableFieldMetaEntity, DbTableFieldMetaDTO> {
}
