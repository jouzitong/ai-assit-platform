package ai.platform.aiassit.chat.core.workflow.node.impl;

import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.chat.core.workflow.node.BaseWorkflowNode;
import ai.platform.aiassit.chat.core.workflow.support.WorkflowHistoryRecorder;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactStage;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * SQL 校验节点，负责做本地安全兜底，并在必要时回跳生成节点。
 *
 * <p>功能：</p>
 * <ul>
 *     <li>对候选 SQL 做规范化处理。</li>
 *     <li>执行本地安全校验，例如只允许 SELECT/WITH、禁止危险关键字、禁止多语句。</li>
 *     <li>校验通过时产出 validatedSql，校验失败时沉淀反馈信息。</li>
 *     <li>在重试次数未超限时回跳到 {@code Sql-Generate} 重新生成。</li>
 * </ul>
 *
 * <p>边界描述：</p>
 * <ul>
 *     <li>只负责安全与结构兜底，不负责重新生成 SQL 内容本身。</li>
 *     <li>不执行 SQL，不判断业务结果是否正确或是否命中真实数据。</li>
 *     <li>回跳控制只针对生成阶段，不跨阶段改写规划或知识上下文。</li>
 * </ul>
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Service
public class SqlValidateNode extends BaseWorkflowNode {

    private static final String RETRY_KEY = "sqlValidateRetryCount";
    private static final int MAX_RETRY_COUNT = 2;

    private final WorkflowHistoryRecorder historyRecorder;

    public SqlValidateNode(WorkflowHistoryRecorder historyRecorder) {
        this.historyRecorder = historyRecorder;
    }

    @Override
    protected NodeResult doExecute(WorkflowContext context) {
        String generatedSql = context.getGeneratedSql();
        if (!StringUtils.hasText(generatedSql)) {
            return NodeResult.fail("generatedSql is required");
        }

        String normalizedSql = normalizeSql(generatedSql);
        String validationError = validateSql(normalizedSql);
        context.setSqlValidationError(validationError);
        context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_VALIDATE.getNodeCode()).setStatus(validationError == null ? "SUCCESS" : "FAILED");
        context.put(WorkflowContextKeys.SqlValidate.VALIDATION_ERROR, validationError);

        if (validationError == null) {
            context.setValidatedSql(normalizedSql);
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.SQL_VALIDATED.name(),
                    AiChatArtifactStage.SQL_VALIDATE.name(),
                    "SQL 校验通过",
                    normalizedSql,
                    AiChatContentFormat.SQL.name(),
                    true,
                    "SUCCESS",
                    context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                    null
            );
            return NodeResult.success(null);
        }

        int retryCount = nextRetryCount(context);
        context.put(RETRY_KEY, retryCount);
        historyRecorder.saveArtifact(
                context,
                AiChatArtifactType.SQL_VALIDATION.name(),
                AiChatArtifactStage.SQL_VALIDATE.name(),
                "SQL 校验失败",
                validationError,
                AiChatContentFormat.PLAIN_TEXT.name(),
                true,
                "FAILED",
                context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                normalizedSql
        );
        if (retryCount <= MAX_RETRY_COUNT) {
            context.put(WorkflowContextKeys.SqlGenerate.FEEDBACK, validationError);
            return NodeResult.success(WorkflowNodeCodes.SQL_GENERATE.getNodeCode());
        }
        return NodeResult.fail(validationError);
    }

    @Override
    public String code() {
        return WorkflowNodeCodes.SQL_VALIDATE.getNodeCode();
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
