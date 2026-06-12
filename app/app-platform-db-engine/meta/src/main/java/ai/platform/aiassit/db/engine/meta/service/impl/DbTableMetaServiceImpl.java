package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.convert.DbTableMetaConvert;
import ai.platform.aiassit.db.engine.meta.entity.DbTableMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableMetaDTO;
import ai.platform.aiassit.db.engine.meta.mapper.DbTableMetaMapper;
import ai.platform.aiassit.db.engine.meta.service.DbTableMetaService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class DbTableMetaServiceImpl
        extends BaseMapperService<DbTableMetaEntity, DbTableMetaMapper, DbTableMetaDTO>
        implements DbTableMetaService {

    private final DbTableMetaConvert dbTableMetaConvert;

    public DbTableMetaServiceImpl(DbTableMetaConvert dbTableMetaConvert) {
        this.dbTableMetaConvert = dbTableMetaConvert;
    }

    @Override
    protected IConvert<DbTableMetaEntity, DbTableMetaDTO> convert() {
        return dbTableMetaConvert;
    }

}
