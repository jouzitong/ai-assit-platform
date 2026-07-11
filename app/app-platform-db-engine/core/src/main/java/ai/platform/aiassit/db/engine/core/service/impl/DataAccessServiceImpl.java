package ai.platform.aiassit.db.engine.core.service.impl;

import ai.platform.aiassit.db.engine.core.dataaccess.DataReadExecutionPipeline;
import ai.platform.aiassit.db.engine.core.service.DataAccessService;
import ai.platform.aiassit.db.engine.core.support.DbAccessContextAssembler;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.request.DataReadCommand;
import ai.platform.aiassit.db.engine.executor.spi.result.DataReadResult;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbDataSourceDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbDataSourceQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbDataSourceService;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DataAccessServiceImpl implements DataAccessService {

    private final DbDataSourceService dataSourceService;
    private final DbAccessContextAssembler contextAssembler;
    private final DataReadExecutionPipeline executionPipeline;

    public DataAccessServiceImpl(
            DbDataSourceService dataSourceService,
            DbAccessContextAssembler contextAssembler,
            DataReadExecutionPipeline executionPipeline
    ) {
        this.dataSourceService = dataSourceService;
        this.contextAssembler = contextAssembler;
        this.executionPipeline = executionPipeline;
    }

    @Override
    public DataReadResult read(String sourceKey, DataReadCommand command) {
        try {
            DbAccessContext context = resolveContext(sourceKey);
            return executionPipeline.execute(context, command);
        } catch (DbAccessException ex) {
            throw new BizException(ex);
        }
    }

    private DbAccessContext resolveContext(String sourceKey) throws DbAccessException {
        if (!StringUtils.hasText(sourceKey)) {
            throw new DbAccessException("sourceKey 不能为空");
        }
        DbDataSourceQueryRequest query = new DbDataSourceQueryRequest();
        query.setSourceKey(sourceKey.trim());
        DbDataSourceDTO dataSource = dataSourceService.get(query);
        if (dataSource == null) {
            throw new DbAccessException("未找到数据源: " + sourceKey);
        }
        if (!Boolean.TRUE.equals(dataSource.getEnabled())) {
            throw new DbAccessException("数据源未启用: " + sourceKey);
        }
        return contextAssembler.toContext(dataSource);
    }
}
