package ai.platform.aiassit.db.engine.executor.jdbc.support;

import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessContext;
import ai.platform.aiassit.db.engine.executor.spi.model.DbAccessDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcDatabaseProfileTest {

    @ParameterizedTest
    @MethodSource("defaultJdbcUrls")
    void resolvesDefaultJdbcUrl(
            JdbcDatabaseProfile profile,
            String expectedUrl
    ) throws DbAccessException {
        DbAccessContext context = context(profile.dbType(), DbAccessDatabase.builder()
                .host("db.example.com")
                .databaseName("application")
                .build());

        assertThat(profile.resolveJdbcUrl(context)).isEqualTo(expectedUrl);
    }

    @Test
    void explicitJdbcUrlWinsOverEndpointAndHostConfiguration() throws DbAccessException {
        DbAccessContext context = context(DbAccessDbType.POSTGRESQL, DbAccessDatabase.builder()
                .jdbcUrl("  jdbc:postgresql://primary.example.com:6432/custom  ")
                .host("ignored.example.com")
                .port(9999)
                .databaseName("ignored")
                .build());
        context.setEndpoint("jdbc:postgresql://also-ignored.example.com/db");

        assertThat(JdbcDatabaseProfile.POSTGRESQL.resolveJdbcUrl(context))
                .isEqualTo("jdbc:postgresql://primary.example.com:6432/custom");
    }

    @Test
    void jdbcEndpointIsUsedWhenDatabaseUrlIsAbsent() throws DbAccessException {
        DbAccessContext context = context(DbAccessDbType.ORACLE, null);
        context.setEndpoint("jdbc:oracle:thin:@//oracle.example.com:1522/service");

        assertThat(JdbcDatabaseProfile.ORACLE.resolveJdbcUrl(context))
                .isEqualTo("jdbc:oracle:thin:@//oracle.example.com:1522/service");
    }

    @Test
    void customPortOverridesProfileDefault() throws DbAccessException {
        DbAccessContext context = context(DbAccessDbType.DM8, DbAccessDatabase.builder()
                .host("dm.example.com")
                .port(6236)
                .build());

        assertThat(JdbcDatabaseProfile.DM8.resolveJdbcUrl(context))
                .isEqualTo("jdbc:dm://dm.example.com:6236");
    }

    @Test
    void missingHostOrDatabaseNameIsRejected() {
        DbAccessContext context = context(DbAccessDbType.POSTGRESQL, DbAccessDatabase.builder()
                .host("db.example.com")
                .build());

        assertThatThrownBy(() -> JdbcDatabaseProfile.POSTGRESQL.resolveJdbcUrl(context))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("缺少 POSTGRESQL 连接配置");
    }

    @Test
    void supportsAndRequireUseTheDeclaredProductSet() throws DbAccessException {
        assertThat(JdbcDatabaseProfile.supports(DbAccessDbType.POSTGRESQL)).isTrue();
        assertThat(JdbcDatabaseProfile.supports(DbAccessDbType.SHENTONG)).isTrue();
        assertThat(JdbcDatabaseProfile.supports(DbAccessDbType.MYSQL)).isFalse();
        assertThat(JdbcDatabaseProfile.supports(DbAccessDbType.MONGODB)).isFalse();
        assertThat(JdbcDatabaseProfile.supports(null)).isFalse();
        assertThat(JdbcDatabaseProfile.require(DbAccessDbType.OCEANBASE))
                .isSameAs(JdbcDatabaseProfile.OCEANBASE);

        assertThatThrownBy(() -> JdbcDatabaseProfile.require(DbAccessDbType.MYSQL))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("不支持的 JDBC 数据库类型: MYSQL");
    }

    @ParameterizedTest
    @MethodSource("compatibilityModes")
    void resolvesConfiguredCompatibilityMode(
            Object configuredMode,
            JdbcDatabaseProfile.DdlFamily expectedFamily
    ) throws DbAccessException {
        DbAccessContext context = context(DbAccessDbType.OCEANBASE, null);
        context.setAttributes(Map.of("compatibilityMode", configuredMode));

        assertThat(JdbcDatabaseProfile.OCEANBASE.resolveFamily(context)).isEqualTo(expectedFamily);
    }

    @Test
    void compatibilityModeTakesPrecedenceOverLegacyDdlDialect() throws DbAccessException {
        DbAccessContext context = context(DbAccessDbType.GAUSSDB, null);
        context.setAttributes(Map.of(
                "compatibilityMode", "oracle",
                "ddlDialect", "mysql"
        ));

        assertThat(JdbcDatabaseProfile.GAUSSDB.resolveFamily(context))
                .isEqualTo(JdbcDatabaseProfile.DdlFamily.ORACLE);
    }

    @Test
    void defaultsAndInvalidCompatibilityModeAreHandledExplicitly() throws DbAccessException {
        assertThat(JdbcDatabaseProfile.OCEANBASE.resolveFamily(context(DbAccessDbType.OCEANBASE, null)))
                .isEqualTo(JdbcDatabaseProfile.DdlFamily.MYSQL);
        assertThat(JdbcDatabaseProfile.DM8.resolveFamily(context(DbAccessDbType.DM8, null)))
                .isEqualTo(JdbcDatabaseProfile.DdlFamily.ORACLE);

        DbAccessContext context = context(DbAccessDbType.GAUSSDB, null);
        context.setAttributes(Map.of("compatibilityMode", "db2"));
        assertThatThrownBy(() -> JdbcDatabaseProfile.GAUSSDB.resolveFamily(context))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("不支持的 compatibilityMode/ddlDialect: db2");
    }

    private static Stream<Arguments> defaultJdbcUrls() {
        return Stream.of(
                Arguments.of(JdbcDatabaseProfile.POSTGRESQL, "jdbc:postgresql://db.example.com:5432/application"),
                Arguments.of(JdbcDatabaseProfile.ORACLE, "jdbc:oracle:thin:@//db.example.com:1521/application"),
                Arguments.of(JdbcDatabaseProfile.DM8, "jdbc:dm://db.example.com:5236"),
                Arguments.of(JdbcDatabaseProfile.KINGBASE_ES, "jdbc:kingbase8://db.example.com:54321/application"),
                Arguments.of(JdbcDatabaseProfile.GAUSSDB, "jdbc:gaussdb://db.example.com:8000/application"),
                Arguments.of(JdbcDatabaseProfile.OCEANBASE, "jdbc:oceanbase://db.example.com:2881/application"),
                Arguments.of(JdbcDatabaseProfile.TDSQL, "jdbc:mysql://db.example.com:3306/application"),
                Arguments.of(JdbcDatabaseProfile.GOLDENDB, "jdbc:mysql://db.example.com:3306/application"),
                Arguments.of(JdbcDatabaseProfile.GBASE, "jdbc:gbase://db.example.com:5258/application"),
                Arguments.of(JdbcDatabaseProfile.SHENTONG, "jdbc:oscar://db.example.com:2003/application")
        );
    }

    private static Stream<Arguments> compatibilityModes() {
        return Stream.of(
                Arguments.of("mysql", JdbcDatabaseProfile.DdlFamily.MYSQL),
                Arguments.of("oracle", JdbcDatabaseProfile.DdlFamily.ORACLE),
                Arguments.of("postgresql", JdbcDatabaseProfile.DdlFamily.POSTGRESQL),
                Arguments.of("postgres", JdbcDatabaseProfile.DdlFamily.POSTGRESQL),
                Arguments.of("PG", JdbcDatabaseProfile.DdlFamily.POSTGRESQL),
                Arguments.of("ansi", JdbcDatabaseProfile.DdlFamily.ANSI)
        );
    }

    private static DbAccessContext context(DbAccessDbType dbType, DbAccessDatabase database) {
        return DbAccessContext.builder()
                .dbType(dbType)
                .database(database)
                .build();
    }
}
