package ai.platform.aiassit.db.engine.core.service;

import ai.platform.aiassit.db.engine.executor.spi.request.DeleteTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbAccessExecutor;
import ai.platform.aiassit.db.engine.executor.spi.request.ExecuteRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTableIndexesRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTablesRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.QueryRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.SaveTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.SaveTableRequest;
import ai.platform.aiassit.db.engine.executor.spi.result.DeleteTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ExecuteResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ListTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ListTableIndexesResult;
import ai.platform.aiassit.db.engine.executor.spi.result.ListTablesResult;
import ai.platform.aiassit.db.engine.executor.spi.result.QueryResult;
import ai.platform.aiassit.db.engine.executor.spi.result.SaveTableColumnsResult;
import ai.platform.aiassit.db.engine.executor.spi.result.SaveTableResult;
import ai.platform.aiassit.db.engine.executor.spi.result.TestConnectionResult;
import ai.platform.aiassit.db.engine.meta.entity.dto.DbDataSourceDTO;

public interface DbAccessService {

    DbAccessDbType getDbType(String sourceKey);

    TestConnectionResult testConnection(String sourceKey);

    TestConnectionResult testConnection(DbDataSourceDTO dataSource);

    ListTablesResult listTables(String sourceKey, ListTablesRequest request);

    ListTableColumnsResult listTableColumns(String sourceKey, ListTableColumnsRequest request);

    ListTableIndexesResult listTableIndexes(String sourceKey, ListTableIndexesRequest request);

    QueryResult query(String sourceKey, QueryRequest request);

    ExecuteResult execute(String sourceKey, ExecuteRequest request);

    SaveTableResult saveTable(String sourceKey, SaveTableRequest request);

    SaveTableColumnsResult saveTableColumns(String sourceKey, SaveTableColumnsRequest request);

    DeleteTableColumnsResult deleteTableColumns(String sourceKey, DeleteTableColumnsRequest request);

    DbAccessExecutor getExecutor(String sourceKey);
}
