package ai.platform.aiassit.db.engine.executor.jdbc.support;

import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbTableColumnDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcDdlSupportTest {

    @Test
    void quotesIdentifiersForMysqlAndStandardFamilies() throws DbAccessException {
        JdbcDdlSupport mysql = ddl(JdbcDatabaseProfile.DdlFamily.MYSQL);
        JdbcDdlSupport postgresql = ddl(JdbcDatabaseProfile.DdlFamily.POSTGRESQL);

        assertThat(mysql.quoteIdentifier("用户_1")).isEqualTo("`用户_1`");
        assertThat(mysql.quoteResource("catalog.schema.orders"))
                .isEqualTo("`catalog`.`schema`.`orders`");
        assertThat(postgresql.quoteIdentifier("order")).isEqualTo("\"order\"");
        assertThat(postgresql.quoteResource("business.orders"))
                .isEqualTo("\"business\".\"orders\"");
    }

    @ParameterizedTest
    @MethodSource("unsafeIdentifiers")
    void rejectsIdentifierInjection(String identifier) {
        JdbcDdlSupport ddl = ddl(JdbcDatabaseProfile.DdlFamily.POSTGRESQL);

        assertThatThrownBy(() -> ddl.quoteIdentifier(identifier))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("标识符不合法");
    }

    @Test
    void rejectsMalformedOrOverqualifiedResources() {
        JdbcDdlSupport ddl = ddl(JdbcDatabaseProfile.DdlFamily.MYSQL);

        assertThatThrownBy(() -> ddl.quoteResource("catalog.schema.table.extra"))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("最多允许");
        assertThatThrownBy(() -> ddl.quoteResource("schema..table"))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("标识符不合法");
    }

    @ParameterizedTest
    @MethodSource("createTableStatements")
    void createsFamilySpecificTableDdl(
            JdbcDatabaseProfile.DdlFamily family,
            String expectedSql
    ) throws DbAccessException {
        assertThat(ddl(family).createTable("biz", "orders", "订单'表", tableColumns()))
                .isEqualTo(expectedSql);
    }

    @ParameterizedTest
    @MethodSource("alterColumnStatements")
    void altersColumnsUsingTheSelectedDdlFamily(
            JdbcDatabaseProfile.DdlFamily family,
            List<String> expectedSql
    ) throws DbAccessException {
        DbTableColumnDefinition column = DbTableColumnDefinition.builder()
                .columnName("display_name")
                .dataType("varchar")
                .columnLength(100)
                .nullable(false)
                .defaultValue("guest")
                .columnComment("显示'名")
                .build();

        assertThat(ddl(family).alterColumn("biz", "users", column))
                .containsExactlyElementsOf(expectedSql);
    }

    @Test
    void emitsFamilySpecificTableAndColumnComments() throws DbAccessException {
        DbTableColumnDefinition column = DbTableColumnDefinition.builder()
                .columnName("name")
                .dataType("varchar")
                .columnLength(50)
                .columnComment("显示名")
                .build();
        JdbcDdlSupport mysql = ddl(JdbcDatabaseProfile.DdlFamily.MYSQL);
        JdbcDdlSupport oracle = ddl(JdbcDatabaseProfile.DdlFamily.ORACLE);

        assertThat(mysql.tableComment("biz", "users", "用户表"))
                .containsExactly("ALTER TABLE `biz`.`users` COMMENT='用户表'");
        assertThat(mysql.columnComment("biz", "users", column)).isEmpty();
        assertThat(oracle.tableComment("biz", "users", "用户表"))
                .containsExactly("COMMENT ON TABLE \"biz\".\"users\" IS '用户表'");
        assertThat(oracle.columnComment("biz", "users", column))
                .containsExactly("COMMENT ON COLUMN \"biz\".\"users\".\"name\" IS '显示名'");
    }

    @ParameterizedTest
    @MethodSource("pagedQueries")
    void rendersFamilySpecificPagination(
            JdbcDatabaseProfile.DdlFamily family,
            String expectedSql
    ) {
        assertThat(ddl(family).pagedQuery("SELECT * FROM orders"))
                .isEqualTo(expectedSql);
    }

    @Test
    void rejectsInjectedDataTypesAndInvalidSizes() {
        JdbcDdlSupport ddl = ddl(JdbcDatabaseProfile.DdlFamily.POSTGRESQL);
        DbTableColumnDefinition injectedType = DbTableColumnDefinition.builder()
                .columnName("name")
                .dataType("VARCHAR(20); DROP TABLE users")
                .build();
        DbTableColumnDefinition invalidLength = DbTableColumnDefinition.builder()
                .columnName("name")
                .dataType("VARCHAR")
                .columnLength(0)
                .build();

        assertThatThrownBy(() -> ddl.createTable("biz", "safe_table", null, List.of(injectedType)))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("字段类型不合法");
        assertThatThrownBy(() -> ddl.createTable("biz", "safe_table", null, List.of(invalidLength)))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("columnLength 必须大于 0");
    }

    @Test
    void rejectsEmptyColumnsAndPrimaryKeys() {
        JdbcDdlSupport ddl = ddl(JdbcDatabaseProfile.DdlFamily.MYSQL);

        assertThatThrownBy(() -> ddl.createTable("biz", "empty_table", null, List.of()))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("字段列表不能为空");
        assertThatThrownBy(() -> ddl.addPrimaryKey("biz", "users", List.of()))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("主键字段不能为空");
    }

    private static Stream<Arguments> unsafeIdentifiers() {
        return Stream.of(
                Arguments.of("users; DROP TABLE audit"),
                Arguments.of("users--"),
                Arguments.of("users.name"),
                Arguments.of("name value"),
                Arguments.of("1users"),
                Arguments.of("")
        );
    }

    private static Stream<Arguments> createTableStatements() {
        return Stream.of(
                Arguments.of(
                        JdbcDatabaseProfile.DdlFamily.MYSQL,
                        "CREATE TABLE `biz`.`orders` (`id` BIGINT AUTO_INCREMENT NOT NULL, "
                                + "`name` VARCHAR(64) DEFAULT 'O''Reilly' NOT NULL COMMENT '显示名', "
                                + "`amount` DECIMAL(12,2) DEFAULT 0.00 NULL, PRIMARY KEY (`id`)) COMMENT='订单''表'"
                ),
                Arguments.of(
                        JdbcDatabaseProfile.DdlFamily.POSTGRESQL,
                        "CREATE TABLE \"biz\".\"orders\" (\"id\" BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL, "
                                + "\"name\" VARCHAR(64) DEFAULT 'O''Reilly' NOT NULL, "
                                + "\"amount\" DECIMAL(12,2) DEFAULT 0.00 NULL, PRIMARY KEY (\"id\"))"
                ),
                Arguments.of(
                        JdbcDatabaseProfile.DdlFamily.ORACLE,
                        "CREATE TABLE \"biz\".\"orders\" (\"id\" BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL, "
                                + "\"name\" VARCHAR(64) DEFAULT 'O''Reilly' NOT NULL, "
                                + "\"amount\" DECIMAL(12,2) DEFAULT 0.00 NULL, PRIMARY KEY (\"id\"))"
                )
        );
    }

    private static Stream<Arguments> alterColumnStatements() {
        return Stream.of(
                Arguments.of(
                        JdbcDatabaseProfile.DdlFamily.MYSQL,
                        List.of("ALTER TABLE `biz`.`users` MODIFY COLUMN `display_name` VARCHAR(100) "
                                + "DEFAULT 'guest' NOT NULL COMMENT '显示''名'")
                ),
                Arguments.of(
                        JdbcDatabaseProfile.DdlFamily.POSTGRESQL,
                        List.of(
                                "ALTER TABLE \"biz\".\"users\" ALTER COLUMN \"display_name\" TYPE VARCHAR(100)",
                                "ALTER TABLE \"biz\".\"users\" ALTER COLUMN \"display_name\" SET NOT NULL",
                                "ALTER TABLE \"biz\".\"users\" ALTER COLUMN \"display_name\" SET DEFAULT 'guest'",
                                "COMMENT ON COLUMN \"biz\".\"users\".\"display_name\" IS '显示''名'"
                        )
                ),
                Arguments.of(
                        JdbcDatabaseProfile.DdlFamily.ORACLE,
                        List.of(
                                "ALTER TABLE \"biz\".\"users\" MODIFY (\"display_name\" VARCHAR(100) DEFAULT 'guest' NOT NULL)",
                                "COMMENT ON COLUMN \"biz\".\"users\".\"display_name\" IS '显示''名'"
                        )
                )
        );
    }

    private static Stream<Arguments> pagedQueries() {
        return Stream.of(
                Arguments.of(
                        JdbcDatabaseProfile.DdlFamily.MYSQL,
                        "SELECT * FROM orders LIMIT ? OFFSET ?"
                ),
                Arguments.of(
                        JdbcDatabaseProfile.DdlFamily.POSTGRESQL,
                        "SELECT * FROM orders LIMIT ? OFFSET ?"
                ),
                Arguments.of(
                        JdbcDatabaseProfile.DdlFamily.ORACLE,
                        "SELECT * FROM orders OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"
                )
        );
    }

    private static List<DbTableColumnDefinition> tableColumns() {
        return List.of(
                DbTableColumnDefinition.builder()
                        .columnName("id")
                        .dataType("bigint")
                        .nullable(false)
                        .primaryKey(true)
                        .autoIncrement(true)
                        .build(),
                DbTableColumnDefinition.builder()
                        .columnName("name")
                        .dataType("varchar")
                        .columnLength(64)
                        .nullable(false)
                        .defaultValue("O'Reilly")
                        .columnComment("显示名")
                        .build(),
                DbTableColumnDefinition.builder()
                        .columnName("amount")
                        .dataType("decimal")
                        .columnPrecision(12)
                        .columnScale(2)
                        .nullable(true)
                        .defaultValue("0.00")
                        .build()
        );
    }

    private static JdbcDdlSupport ddl(JdbcDatabaseProfile.DdlFamily family) {
        return new JdbcDdlSupport(family);
    }
}
