package ai.platform.aiassit.db.engine.core.service.impl;

import ai.platform.aiassit.db.engine.core.registry.DbAccessProviderRegistry;
import ai.platform.aiassit.db.engine.core.service.DbAccessService;
import ai.platform.aiassit.db.engine.core.support.DbAccessContextAssembler;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.request.DeleteTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbAccessExecutor;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbAccessProvider;
import ai.platform.aiassit.db.engine.executor.spi.request.ExecuteRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTablesRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.QueryRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.SaveTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.SaveTableRequest;
import ai.platform.aiassit.db.engine.executor.spi.result.DeleteTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ExecuteResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ListTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ListTablesResult;
import ai.platform.aiassit.db.engine.executor.spi.result.QueryResult;
import ai.platform.aiassit.db.engine.executor.spi.result.SaveTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.SaveTableResult;
import ai.platform.aiassit.db.engine.executor.spi.result.TestConnectionResult;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbDataSourceDTO;
import ai.platform.aiassit.db.engine.meta.entity.req.DbDataSourceQueryRequest;
import ai.platform.aiassit.db.engine.meta.service.DbDataSourceService;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbAccessServiceImpl implements DbAccessService {

    private final DbDataSourceService dataSourceService;
    private final DbAccessProviderRegistry providerRegistry;
    private final DbAccessContextAssembler contextAssembler;

    public DbAccessServiceImpl(
            DbDataSourceService dataSourceService,
            DbAccessProviderRegistry providerRegistry,
            DbAccessContextAssembler contextAssembler
    ) {
        this.dataSourceService = dataSourceService;
        this.providerRegistry = providerRegistry;
        this.contextAssembler = contextAssembler;
    }

    @Override
    public DbAccessDbType getDbType(String sourceKey) {
        try {
            return resolveContext(sourceKey).getDbType();
        } catch (DbAccessException ex) {
            throw toBizException(ex);
        }
    }

    @Override
    public TestConnectionResult testConnection(String sourceKey) {
        try {
            return getExecutor(sourceKey).testConnection();
        } catch (DbAccessException ex) {
            throw toBizException(ex);
        }
    }

    @Override
    public TestConnectionResult testConnection(DbDataSourceDTO dataSource) {
        try {
            DbAccessContext context = contextAssembler.toContext(dataSource);
            return resolveProvider(context).createExecutor(context).testConnection();
        } catch (DbAccessException ex) {
            throw toBizException(ex);
        }
    }

    @Override
    public ListTablesResult listTables(String sourceKey, ListTablesRequest request) {
        try {
            return getExecutor(sourceKey).listTables(request);
        } catch (DbAccessException ex) {
            throw toBizException(ex);
        }
    }

    @Override
    public ListTableColumnsResult listTableColumns(String sourceKey, ListTableColumnsRequest request) {
        try {
            return getExecutor(sourceKey).listTableColumns(request);
        } catch (DbAccessException ex) {
            throw toBizException(ex);
        }
    }

    @Override
    public QueryResult query(String sourceKey, QueryRequest request) {
        try {
            return getExecutor(sourceKey).query(request);
        } catch (DbAccessException ex) {
            throw toBizException(ex);
        }
    }

    @Override
    public ExecuteResult execute(String sourceKey, ExecuteRequest request) {
        try {
            return getExecutor(sourceKey).execute(request);
        } catch (DbAccessException ex) {
            throw toBizException(ex);
        }
    }

    @Override
    public SaveTableResult saveTable(String sourceKey, SaveTableRequest request) {
        try {
            return getExecutor(sourceKey).saveTable(request);
        } catch (DbAccessException ex) {
            throw toBizException(ex);
        }
    }

    @Override
    public SaveTableColumnsResult saveTableColumns(String sourceKey, SaveTableColumnsRequest request) {
        try {
            return getExecutor(sourceKey).saveTableColumns(request);
        } catch (DbAccessException ex) {
            throw toBizException(ex);
        }
    }

    @Override
    public DeleteTableColumnsResult deleteTableColumns(String sourceKey, DeleteTableColumnsRequest request) {
        try {
            return getExecutor(sourceKey).deleteTableColumns(request);
        } catch (DbAccessException ex) {
            throw toBizException(ex);
        }
    }

    @Override
    public DbAccessExecutor getExecutor(String sourceKey) {
        try {
            DbAccessContext context = resolveContext(sourceKey);
            return resolveProvider(context).createExecutor(context);
        } catch (DbAccessException ex) {
            throw toBizException(ex);
        }
    }

    private DbAccessContext resolveContext(String sourceKey) throws DbAccessException {
        DbDataSourceDTO dataSource = findDataSource(sourceKey);
        if (!Boolean.TRUE.equals(dataSource.getEnabled())) {
            throw new DbAccessException("数据源未启用: " + sourceKey);
        }
        return contextAssembler.toContext(dataSource);
    }

    private DbAccessProvider resolveProvider(DbAccessContext context) throws DbAccessException {
        return providerRegistry.getProvider(context.getSourceType(), context.getDbType());
    }

    private DbDataSourceDTO findDataSource(String sourceKey) throws DbAccessException {
        if (!StringUtils.hasText(sourceKey)) {
            throw new DbAccessException("sourceKey 不能为空");
        }
        DbDataSourceQueryRequest query = new DbDataSourceQueryRequest();
        query.setSourceKey(sourceKey.trim());
        DbDataSourceDTO dataSource = dataSourceService.get(query);
        if (dataSource == null) {
            throw new DbAccessException("未找到数据源: " + sourceKey);
        }
        return dataSource;
    }

    private BizException toBizException(DbAccessException ex) {
        return new BizException(ex);
    }
}
