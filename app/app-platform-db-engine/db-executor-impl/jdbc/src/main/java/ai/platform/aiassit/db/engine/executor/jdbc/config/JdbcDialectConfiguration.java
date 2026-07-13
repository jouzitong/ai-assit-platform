package ai.platform.aiassit.db.engine.executor.jdbc.config;

import ai.platform.aiassit.db.engine.executor.jdbc.provider.JdbcDbSqlDialect;
import ai.platform.aiassit.db.engine.executor.spi.enums.DbAccessDbType;
import ai.platform.aiassit.db.engine.executor.spi.provider.DbSqlDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JdbcDialectConfiguration {

    @Bean
    public DbSqlDialect postgresqlDbSqlDialect() {
        return dialect(DbAccessDbType.POSTGRESQL);
    }

    @Bean
    public DbSqlDialect oracleDbSqlDialect() {
        return dialect(DbAccessDbType.ORACLE);
    }

    @Bean
    public DbSqlDialect dm8DbSqlDialect() {
        return dialect(DbAccessDbType.DM8);
    }

    @Bean
    public DbSqlDialect kingbaseEsDbSqlDialect() {
        return dialect(DbAccessDbType.KINGBASE_ES);
    }

    @Bean
    public DbSqlDialect gaussDbSqlDialect() {
        return dialect(DbAccessDbType.GAUSSDB);
    }

    @Bean
    public DbSqlDialect oceanBaseDbSqlDialect() {
        return dialect(DbAccessDbType.OCEANBASE);
    }

    @Bean
    public DbSqlDialect tdSqlDbSqlDialect() {
        return dialect(DbAccessDbType.TDSQL);
    }

    @Bean
    public DbSqlDialect goldenDbSqlDialect() {
        return dialect(DbAccessDbType.GOLDENDB);
    }

    @Bean
    public DbSqlDialect gBaseDbSqlDialect() {
        return dialect(DbAccessDbType.GBASE);
    }

    @Bean
    public DbSqlDialect shentongDbSqlDialect() {
        return dialect(DbAccessDbType.SHENTONG);
    }

    private DbSqlDialect dialect(DbAccessDbType dbType) {
        return new JdbcDbSqlDialect(dbType);
    }
}
