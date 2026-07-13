package ai.platform.aiassit.db.engine.meta.convert;

import ai.platform.aiassit.db.engine.meta.entity.DbTableRelationMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableRelationMetaDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DbTableRelationMetaConvert extends IConvert<DbTableRelationMetaEntity, DbTableRelationMetaDTO> {
}
