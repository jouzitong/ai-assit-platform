package ai.platform.aiassit.db.engine.executor.spi.provider;

import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.request.DeleteTableColumnsRequest;
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

public interface DbAccessExecutor {

    TestConnectionResult testConnection() throws DbAccessException;

    ListTablesResult listTables(ListTablesRequest request) throws DbAccessException;

    ListTableColumnsResult listTableColumns(ListTableColumnsRequest request) throws DbAccessException;

    QueryResult query(QueryRequest request) throws DbAccessException;

    ExecuteResult execute(ExecuteRequest request) throws DbAccessException;

    SaveTableResult saveTable(SaveTableRequest request) throws DbAccessException;

    SaveTableColumnsResult saveTableColumns(SaveTableColumnsRequest request) throws DbAccessException;

    DeleteTableColumnsResult deleteTableColumns(DeleteTableColumnsRequest request) throws DbAccessException;
}
