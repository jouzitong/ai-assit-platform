package ai.platform.aiassit.db.engine.meta.convert;

import ai.platform.aiassit.db.engine.meta.entity.DbTableIndexMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableIndexMetaDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DbTableIndexMetaConvert extends IConvert<DbTableIndexMetaEntity, DbTableIndexMetaDTO> {
}
