package ai.platform.aiassit.db.engine.executor.jdbc.support;

import ai.platform.aiassit.db.engine.executor.spi.exception.DbAccessException;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 对外部传入 SQL 做单语句和操作类型限制。 */
public final class JdbcSqlGuard {

    private static final Set<String> QUERY_OPERATIONS = Set.of("SELECT", "WITH");
    private static final Set<String> EXECUTE_OPERATIONS = Set.of("INSERT", "UPDATE", "DELETE", "MERGE");
    private static final Pattern MUTATING_TOKEN = Pattern.compile("(?i)\\b(INSERT|UPDATE|DELETE|MERGE)\\b");

    private JdbcSqlGuard() {
    }

    public static String validateQuery(String sql) throws DbAccessException {
        String normalized = normalize(sql);
        String operation = firstKeyword(normalized);
        if (!QUERY_OPERATIONS.contains(operation)) {
            throw new DbAccessException("查询只允许执行 SELECT/WITH 语句");
        }
        if ("WITH".equals(operation) && MUTATING_TOKEN.matcher(withoutStringLiterals(normalized)).find()) {
            throw new DbAccessException("查询不允许包含数据修改 CTE");
        }
        return normalized;
    }

    public static String validateExecute(String sql) throws DbAccessException {
        String normalized = normalize(sql);
        if (!EXECUTE_OPERATIONS.contains(firstKeyword(normalized))) {
            throw new DbAccessException("执行只允许 INSERT/UPDATE/DELETE/MERGE 语句");
        }
        return normalized;
    }

    private static String normalize(String sql) throws DbAccessException {
        if (!StringUtils.hasText(sql)) {
            throw new DbAccessException("SQL 不能为空");
        }
        String normalized = sql.trim();
        while (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        if (!StringUtils.hasText(normalized)) {
            throw new DbAccessException("SQL 不能为空");
        }
        if (normalized.contains(";") || normalized.contains("--") || normalized.contains("/*") || normalized.contains("*/")) {
            throw new DbAccessException("暂不支持多语句或包含注释的 SQL");
        }
        return normalized;
    }

    private static String firstKeyword(String sql) {
        int index = 0;
        while (index < sql.length() && !Character.isWhitespace(sql.charAt(index))) {
            index++;
        }
        return sql.substring(0, index).toUpperCase(Locale.ROOT);
    }

    private static String withoutStringLiterals(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        boolean quoted = false;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (current == '\'' && quoted && index + 1 < sql.length() && sql.charAt(index + 1) == '\'') {
                result.append(' ').append(' ');
                index++;
                continue;
            }
            if (current == '\'') {
                quoted = !quoted;
                result.append(' ');
            } else {
                result.append(quoted ? ' ' : current);
            }
        }
        return result.toString();
    }
}
