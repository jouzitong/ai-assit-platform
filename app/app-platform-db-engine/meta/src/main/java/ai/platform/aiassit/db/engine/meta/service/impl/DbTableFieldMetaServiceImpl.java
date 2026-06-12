package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.convert.DbTableFieldMetaConvert;
import ai.platform.aiassit.db.engine.meta.entity.DbTableFieldMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableFieldMetaDTO;
import ai.platform.aiassit.db.engine.meta.mapper.DbTableFieldMetaMapper;
import ai.platform.aiassit.db.engine.meta.service.DbTableFieldMetaService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class DbTableFieldMetaServiceImpl
        extends BaseMapperService<DbTableFieldMetaEntity, DbTableFieldMetaMapper, DbTableFieldMetaDTO>
        implements DbTableFieldMetaService {

    private final DbTableFieldMetaConvert dbTableFieldMetaConvert;

    public DbTableFieldMetaServiceImpl(DbTableFieldMetaConvert dbTableFieldMetaConvert) {
        this.dbTableFieldMetaConvert = dbTableFieldMetaConvert;
    }

    @Override
    protected IConvert<DbTableFieldMetaEntity, DbTableFieldMetaDTO> convert() {
        return dbTableFieldMetaConvert;
    }

}
