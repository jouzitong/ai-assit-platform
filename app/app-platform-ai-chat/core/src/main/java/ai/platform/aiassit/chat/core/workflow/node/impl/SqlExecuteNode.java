package ai.platform.aiassit.chat.core.workflow.node.impl;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.node.BaseWorkflowNode;
import ai.platform.aiassit.chat.core.workflow.support.WorkflowHistoryRecorder;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactStage;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
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
 * <p>功能：</p>
 * <ul>
 *     <li>消费已通过校验的 SQL。</li>
 *     <li>优先透传上游显式提供的预执行结果。</li>
 *     <li>在缺少真实执行能力时返回结构化降级结果，并明确标记为 SKIPPED。</li>
 *     <li>将执行状态与执行结果写入 {@link WorkflowContext}，供渲染节点消费。</li>
 * </ul>
 *
 * <p>边界描述：</p>
 * <ul>
 *     <li>当前阶段只做执行结果收敛，不伪装成真实 db-engine 执行器。</li>
 *     <li>不重新修正 SQL，不补造结果数据，不承担最终答案组织。</li>
 *     <li>执行能力缺失时必须显式降级，不能隐式吞掉状态。</li>
 * </ul>
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Service
public class SqlExecuteNode extends BaseWorkflowNode {

    private final WorkflowHistoryRecorder historyRecorder;

    public SqlExecuteNode(WorkflowHistoryRecorder historyRecorder) {
        this.historyRecorder = historyRecorder;
    }

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
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.SQL_EXEC_RESULT.name(),
                    AiChatArtifactStage.SQL_EXEC.name(),
                    "SQL 执行结果",
                    providedResult,
                    AiChatContentFormat.JSON.name(),
                    true,
                    "SUCCESS",
                    context.getCurrentUserMessage() == null ? null : context.getCurrentUserMessage().getMessageCode(),
                    validatedSql
            );
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
        historyRecorder.saveArtifact(
                context,
                AiChatArtifactType.SQL_EXEC_RESULT.name(),
                AiChatArtifactStage.SQL_EXEC.name(),
                "SQL 执行结果",
                degradedResult,
                AiChatContentFormat.JSON.name(),
                true,
                "SKIPPED",
                context.getCurrentUserMessage() == null ? null : context.getCurrentUserMessage().getMessageCode(),
                validatedSql
        );
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
