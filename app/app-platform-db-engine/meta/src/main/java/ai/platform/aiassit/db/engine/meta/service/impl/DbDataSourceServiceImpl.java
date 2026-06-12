package ai.platform.aiassit.db.engine.meta.service.impl;

import ai.platform.aiassit.db.engine.meta.convert.DbDataSourceConvert;
import ai.platform.aiassit.db.engine.meta.entity.DbDataSourceEntity;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbDataSourceDTO;
import ai.platform.aiassit.db.engine.meta.mapper.DbDataSourceMapper;
import ai.platform.aiassit.db.engine.meta.service.DbDataSourceService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class DbDataSourceServiceImpl
        extends BaseMapperService<DbDataSourceEntity, DbDataSourceMapper, DbDataSourceDTO>
        implements DbDataSourceService {

    private final DbDataSourceConvert dbDataSourceConvert;

    public DbDataSourceServiceImpl(DbDataSourceConvert dbDataSourceConvert) {
        this.dbDataSourceConvert = dbDataSourceConvert;
    }

    @Override
    protected IConvert<DbDataSourceEntity, DbDataSourceDTO> convert() {
        return dbDataSourceConvert;
    }

}
