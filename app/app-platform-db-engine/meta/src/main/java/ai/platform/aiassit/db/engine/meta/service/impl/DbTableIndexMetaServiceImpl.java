package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.convert.DbTableIndexMetaConvert;
import ai.platform.aiassit.db.engine.meta.entity.DbTableIndexMetaEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbTableIndexMetaDTO;
import ai.platform.aiassit.db.engine.meta.mapper.DbTableIndexMetaMapper;
import ai.platform.aiassit.db.engine.meta.service.DbTableIndexMetaService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class DbTableIndexMetaServiceImpl
        extends BaseMapperService<DbTableIndexMetaEntity, DbTableIndexMetaMapper, DbTableIndexMetaDTO>
        implements DbTableIndexMetaService {

    private final DbTableIndexMetaConvert dbTableIndexMetaConvert;

    public DbTableIndexMetaServiceImpl(DbTableIndexMetaConvert dbTableIndexMetaConvert) {
        this.dbTableIndexMetaConvert = dbTableIndexMetaConvert;
    }

    @Override
    protected IConvert<DbTableIndexMetaEntity, DbTableIndexMetaDTO> convert() {
        return dbTableIndexMetaConvert;
    }

}
