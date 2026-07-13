package ai.platform.aiassit.db.engine.executor.jdbc.provider;

import ai.platform.aiassit.db.engine.executor.jdbc.support.JdbcConnectionSupport;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessSourceType;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessAuth;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessDatabase;
import ai.platform.aiassit.db.engine.executor.spi.model.DbTableColumnDefinition;
import ai.platform.aiassit.db.engine.executor.spi.request.DeleteTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ExecuteRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTableIndexesRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.ListTablesRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.QueryRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.SaveTableColumnsRequest;
import ai.platform.aiassit.db.engine.executor.spi.request.SaveTableRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDbAccessExecutorTest {

    @Test
    void executesMetadataDmlQueryAndDdlContractAgainstPostgresqlMode() throws Exception {
        JdbcDbAccessExecutor executor = executor();

        assertThat(executor.testConnection().getSuccess()).isTrue();

        var saveResult = executor.saveTable(SaveTableRequest.builder()
                .schemaName("PUBLIC")
                .tableName("accounts")
                .tableComment("账户")
                .columns(List.of(
                        column("id", "BIGINT", false, true),
                        DbTableColumnDefinition.builder()
                                .columnName("name")
                                .dataType("VARCHAR")
                                .columnLength(80)
                                .nullable(false)
                                .columnComment("名称")
                                .build(),
                        DbTableColumnDefinition.builder()
                                .columnName("balance")
                                .dataType("DECIMAL")
                                .columnPrecision(18)
                                .columnScale(2)
                                .nullable(true)
                                .build()
                ))
                .build());

        assertThat(saveResult.getCreated()).isTrue();
        assertThat(executor.listTables(ListTablesRequest.builder()
                .schemaName("PUBLIC")
                .keyword("account")
                .build()).getTables())
                .extracting("tableName")
                .containsExactly("accounts");
        assertThat(executor.listTableColumns(ListTableColumnsRequest.builder()
                .schemaName("PUBLIC")
                .tableName("accounts")
                .build()).getColumns())
                .extracting("columnName")
                .containsExactly("id", "name", "balance");
        assertThat(executor.listTableIndexes(ListTableIndexesRequest.builder()
                .schemaName("PUBLIC")
                .tableName("accounts")
                .build()).getIndexes()).isNotEmpty();

        var insertResult = executor.execute(ExecuteRequest.builder()
                .sql("INSERT INTO \"PUBLIC\".\"accounts\" (\"id\", \"name\", \"balance\") VALUES (?, ?, ?)")
                .parameters(List.of(1L, "Alice", 12.50))
                .build());
        assertThat(insertResult.getAffectedRows()).isEqualTo(1);

        var queryResult = executor.query(QueryRequest.builder()
                .sql("SELECT \"name\", \"balance\" FROM \"PUBLIC\".\"accounts\" WHERE \"id\" = ?")
                .parameters(List.of(1L))
                .build());
        assertThat(queryResult.getRowCount()).isEqualTo(1);
        assertThat(queryResult.getRows().get(0).get("name")).isEqualTo("Alice");

        var addColumnResult = executor.saveTableColumns(SaveTableColumnsRequest.builder()
                .schemaName("PUBLIC")
                .tableName("accounts")
                .columns(List.of(DbTableColumnDefinition.builder()
                        .columnName("created_at")
                        .dataType("TIMESTAMP")
                        .nullable(true)
                        .build()))
                .build());
        assertThat(addColumnResult.getAffectedColumnCount()).isEqualTo(1);

        var deleteResult = executor.deleteTableColumns(DeleteTableColumnsRequest.builder()
                .schemaName("PUBLIC")
                .tableName("accounts")
                .columnNames(List.of("created_at"))
                .build());
        assertThat(deleteResult.getAffectedColumnCount()).isEqualTo(1);
    }

    private JdbcDbAccessExecutor executor() throws Exception {
        String databaseName = "executor_" + UUID.randomUUID().toString().replace("-", "");
        DbAccessContext context = DbAccessContext.builder()
                .sourceType(DbAccessSourceType.DATABASE)
                .dbType(DbAccessDbType.POSTGRESQL)
                .database(DbAccessDatabase.builder()
                        .jdbcUrl("jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
                        .schemaName("PUBLIC")
                        .build())
                .auth(DbAccessAuth.builder().username("sa").password("").build())
                .attributes(Map.of("driverClass", "org.h2.Driver"))
                .build();
        return new JdbcDbAccessExecutor(new JdbcConnectionSupport(), context);
    }

    private DbTableColumnDefinition column(String name, String type, boolean nullable, boolean primaryKey) {
        return DbTableColumnDefinition.builder()
                .columnName(name)
                .dataType(type)
                .nullable(nullable)
                .primaryKey(primaryKey)
                .build();
    }
}
