package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.convert.DbTableRelationMetaConvert;
import ai.platform.aiassit.db.engine.meta.entity.DbTableRelationMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableRelationMetaDTO;
import ai.platform.aiassit.db.engine.meta.mapper.DbTableRelationMetaMapper;
import ai.platform.aiassit.db.engine.meta.service.DbTableRelationMetaService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class DbTableRelationMetaServiceImpl
        extends BaseMapperService<DbTableRelationMetaEntity, DbTableRelationMetaMapper, DbTableRelationMetaDTO>
        implements DbTableRelationMetaService {

    private final DbTableRelationMetaConvert dbTableRelationMetaConvert;

    public DbTableRelationMetaServiceImpl(DbTableRelationMetaConvert dbTableRelationMetaConvert) {
        this.dbTableRelationMetaConvert = dbTableRelationMetaConvert;
    }

    @Override
    protected IConvert<DbTableRelationMetaEntity, DbTableRelationMetaDTO> convert() {
        return dbTableRelationMetaConvert;
    }
}
