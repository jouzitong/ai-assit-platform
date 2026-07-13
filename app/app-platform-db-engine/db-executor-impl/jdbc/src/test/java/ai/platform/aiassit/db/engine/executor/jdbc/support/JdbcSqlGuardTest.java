package ai.platform.aiassit.db.engine.executor.jdbc.support;

import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcSqlGuardTest {

    @Test
    void queryIsTrimmedAndTrailingTerminatorsAreRemoved() throws DbAccessException {
        assertThat(JdbcSqlGuard.validateQuery("  select * from users;;;  "))
                .isEqualTo("select * from users");
        assertThat(JdbcSqlGuard.validateQuery("\nWITH active AS (SELECT 1) SELECT * FROM active"))
                .isEqualTo("WITH active AS (SELECT 1) SELECT * FROM active");
        assertThat(JdbcSqlGuard.validateQuery("SELECT 'update is only text' AS message"))
                .isEqualTo("SELECT 'update is only text' AS message");
    }

    @Test
    void mutatingCteIsRejectedFromQueryChannel() {
        assertThatThrownBy(() -> JdbcSqlGuard.validateQuery(
                "WITH removed AS (DELETE FROM users RETURNING id) SELECT * FROM removed"))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("数据修改 CTE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO users(id) VALUES (?)",
            "UPDATE users SET name = ? WHERE id = ?",
            "DELETE FROM users WHERE id = ?",
            "MERGE INTO users target USING source ON (target.id = source.id) WHEN MATCHED THEN UPDATE SET target.name = source.name"
    })
    void executeAllowsSupportedMutationOperations(String sql) throws DbAccessException {
        assertThat(JdbcSqlGuard.validateExecute(sql)).isEqualTo(sql);
    }

    @Test
    void queryAndExecuteOperationsCannotBeMixed() {
        assertThatThrownBy(() -> JdbcSqlGuard.validateQuery("DELETE FROM users"))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("查询只允许执行 SELECT/WITH");
        assertThatThrownBy(() -> JdbcSqlGuard.validateExecute("SELECT * FROM users"))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("执行只允许 INSERT/UPDATE/DELETE/MERGE");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", ";;;"})
    void blankSqlIsRejected(String sql) {
        assertThatThrownBy(() -> JdbcSqlGuard.validateQuery(sql))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("SQL 不能为空");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT 1; DELETE FROM users",
            "SELECT * FROM users -- bypass",
            "SELECT /*+ hint */ * FROM users",
            "UPDATE users SET name = 'x' /* comment */ WHERE id = 1"
    })
    void multipleStatementsAndCommentsAreRejected(String sql) {
        assertThatThrownBy(() -> JdbcSqlGuard.validateQuery(sql))
                .isInstanceOf(DbAccessException.class)
                .hasMessageContaining("暂不支持多语句或包含注释的 SQL");
    }

    @Test
    void firstKeywordMayBeSeparatedByAnyWhitespace() throws DbAccessException {
        assertThat(JdbcSqlGuard.validateQuery("SELECT\n* FROM users"))
                .isEqualTo("SELECT\n* FROM users");
        assertThat(JdbcSqlGuard.validateExecute("UPDATE\tusers SET name = ?"))
                .isEqualTo("UPDATE\tusers SET name = ?");
    }
}
