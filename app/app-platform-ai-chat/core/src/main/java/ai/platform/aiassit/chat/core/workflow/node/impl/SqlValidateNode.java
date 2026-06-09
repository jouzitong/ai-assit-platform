package ai.platform.aiassit.chat.core.workflow.node.impl;

import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.node.BaseWorkflowNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * SQL 校验节点，负责做本地安全兜底，并在必要时回跳生成节点。
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Service
public class SqlValidateNode extends BaseWorkflowNode {

    private static final String RETRY_KEY = "sqlValidateRetryCount";
    private static final int MAX_RETRY_COUNT = 2;

    @Override
    protected NodeResult doExecute(WorkflowContext context) {
        String generatedSql = context.getGeneratedSql();
        if (!StringUtils.hasText(generatedSql)) {
            return NodeResult.fail("generatedSql is required");
        }

        String normalizedSql = normalizeSql(generatedSql);
        String validationError = validateSql(normalizedSql);
        context.setSqlValidationError(validationError);
        context.put("sqlValidationError", validationError);

        if (validationError == null) {
            context.setValidatedSql(normalizedSql);
            return NodeResult.success(null);
        }

        int retryCount = nextRetryCount(context);
        context.put(RETRY_KEY, retryCount);
        if (retryCount <= MAX_RETRY_COUNT) {
            context.put("sqlGenerationFeedback", validationError);
            return NodeResult.success("Sql-Generate");
        }
        return NodeResult.fail(validationError);
    }

    @Override
    public String type() {
        return "Sql-Validate";
    }

    @Override
    public int order() {
        return 500;
    }

    private int nextRetryCount(WorkflowContext context) {
        Integer current = context.get(RETRY_KEY);
        return current == null ? 1 : current + 1;
    }

    private String normalizeSql(String sql) {
        return sql == null ? null : sql.trim();
    }

    private String validateSql(String sql) {
        if (!StringUtils.hasText(sql)) {
            return "generated sql is empty";
        }
        String normalized = sql.toUpperCase(Locale.ROOT);
        if (!(normalized.startsWith("SELECT") || normalized.startsWith("WITH") || normalized.startsWith("--"))) {
            return "only SELECT/WITH sql is allowed";
        }
        if (containsForbiddenKeyword(normalized)) {
            return "sql contains forbidden keyword";
        }
        if (containsMultipleStatements(normalized)) {
            return "multiple sql statements are not allowed";
        }
        return null;
    }

    private boolean containsForbiddenKeyword(String sql) {
        return sql.contains(" INSERT ")
                || sql.contains(" UPDATE ")
                || sql.contains(" DELETE ")
                || sql.contains(" DROP ")
                || sql.contains(" ALTER ")
                || sql.contains(" TRUNCATE ")
                || sql.contains(" CREATE ")
                || sql.contains(" MERGE ")
                || sql.contains(" GRANT ")
                || sql.contains(" REVOKE ");
    }

    private boolean containsMultipleStatements(String sql) {
        String trimmed = sql.trim();
        int firstSemicolon = trimmed.indexOf(';');
        return firstSemicolon >= 0 && firstSemicolon < trimmed.length() - 1;
    }
}
