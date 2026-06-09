package ai.platform.aiassit.chat.core.workflow.node.impl;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.node.BaseWorkflowNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 执行节点。
 *
 * <p>当前仓库尚未接入稳定的 NL2SQL 执行 API，因此这里先统一收敛为显式执行结果：
 * 如果上游已提供预执行结果则直接透传，否则返回结构化降级说明，避免伪装成真实执行。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Service
public class SqlExecuteNode extends BaseWorkflowNode {

    @Override
    protected NodeResult doExecute(WorkflowContext context) {
        String validatedSql = context.getValidatedSql();
        if (!StringUtils.hasText(validatedSql)) {
            return NodeResult.fail("validatedSql is required");
        }

        AiChatQueryCommand command = context.getCommand();
        Object providedResult = command == null || command.getExt() == null ? null : command.getExt().get("sqlExecutionResult");
        if (providedResult == null && command != null && command.getExt() != null) {
            providedResult = command.getExt().get("previewRows");
        }

        if (providedResult != null) {
            context.setSqlExecutionStatus("SUCCESS");
            context.setSqlExecutionResult(providedResult);
            context.put("sqlExecutionResult", providedResult);
            return NodeResult.success(null);
        }

        Map<String, Object> degradedResult = new LinkedHashMap<>();
        degradedResult.put("executed", Boolean.FALSE);
        degradedResult.put("status", "SKIPPED");
        degradedResult.put("reason", "db-engine execute api is not integrated in current workflow");
        degradedResult.put("sql", validatedSql);
        degradedResult.put("rows", List.of());

        context.setSqlExecutionStatus("SKIPPED");
        context.setSqlExecutionResult(degradedResult);
        context.put("sqlExecutionResult", degradedResult);
        return NodeResult.success(null);
    }

    @Override
    public String type() {
        return "Sql-Execute";
    }

    @Override
    public int order() {
        return 600;
    }
}
