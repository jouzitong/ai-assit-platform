package ai.platform.aiassit.conversation.workflow.node.impl;

import ai.platform.aiassit.conversation.constant.ConversationEventPhases;
import ai.platform.aiassit.conversation.constant.ConversationEventSources;
import ai.platform.aiassit.conversation.workflow.bean.NodeResult;
import ai.platform.aiassit.conversation.workflow.constants.ConversationRuntimeContextKeys;
import ai.platform.aiassit.conversation.workflow.context.ConversationRuntimeContext;
import ai.platform.aiassit.conversation.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.conversation.workflow.dto.WorkflowEvaluationResponse;
import ai.platform.aiassit.conversation.workflow.evaluate.service.WorkflowResultEvaluateService;
import ai.platform.aiassit.conversation.workflow.node.BaseWorkflowNode;
import ai.platform.aiassit.conversation.workflow.planning.contract.PlanningResult;
import ai.platform.aiassit.conversation.workflow.sql.contract.SqlPreGenerateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 结果评估节点。
 *
 * <p>当前版本先做程序化评估，判断是否具备进入渲染节点的最小条件。</p>
 *
 * @author zhouzhitong
 * @since 2026/7/5
 */
@Service
@Slf4j
public class ResultEvaluateNode extends BaseWorkflowNode {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final WorkflowResultEvaluateService workflowResultEvaluateService;

    public ResultEvaluateNode(WorkflowResultEvaluateService workflowResultEvaluateService) {
        this.workflowResultEvaluateService = workflowResultEvaluateService;
    }

    @Override
    protected NodeResult doExecute(ConversationRuntimeContext context) {
        PlanningResult planningResult = context.get(ConversationRuntimeContextKeys.Planning.QUERY_PLAN_RESULT);
        if (planningResult == null) {
            markFailed(context, "planning result is missing");
            return NodeResult.success(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode());
        }
        if (!StringUtils.hasText(context.getAnalysisResult())) {
            markFailed(context, "analysis result is missing");
            return NodeResult.success(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode());
        }

        SqlPreGenerateResult sqlPreGenerateResult = context.getSqlPreGenerateResult();
        if (sqlPreGenerateResult == null) {
            markFailed(context, "sql pre-generate result is missing");
            return NodeResult.success(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode());
        }
        if (hasBlockingProblem(sqlPreGenerateResult)) {
            String summary = buildProblemSummary(sqlPreGenerateResult);
            markFailed(context, summary);
            return NodeResult.success(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode());
        }
        if (!StringUtils.hasText(context.getGeneratedSql())) {
            markFailed(context, "generated sql is missing");
            return NodeResult.success(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode());
        }

        return evaluateByAi(context, sqlPreGenerateResult);
    }

    @Override
    public String code() {
        return WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode();
    }

    @Override
    public int order() {
        return 500;
    }

    private boolean hasBlockingProblem(SqlPreGenerateResult sqlPreGenerateResult) {
        if (sqlPreGenerateResult == null || CollectionUtils.isEmpty(sqlPreGenerateResult.getProblems())) {
            return false;
        }
        return sqlPreGenerateResult.getProblems().stream()
                .filter(problem -> problem != null)
                .anyMatch(problem -> Boolean.TRUE.equals(problem.getBlocking()));
    }

    private String buildProblemSummary(SqlPreGenerateResult sqlPreGenerateResult) {
        if (sqlPreGenerateResult == null || CollectionUtils.isEmpty(sqlPreGenerateResult.getProblems())) {
            return "sql pre-generate has blocking problems";
        }
        return sqlPreGenerateResult.getProblems().stream()
                .filter(problem -> problem != null && Boolean.TRUE.equals(problem.getBlocking()))
                .map(problem -> StringUtils.hasText(problem.getMessage()) ? problem.getMessage() : problem.getType())
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("sql pre-generate has blocking problems");
    }

    private String buildSuccessSummary(SqlPreGenerateResult sqlPreGenerateResult) {
        String mainTable = sqlPreGenerateResult == null || sqlPreGenerateResult.getMainTable() == null
                ? null
                : sqlPreGenerateResult.getMainTable().getTableName();
        int relationTableCount = sqlPreGenerateResult == null || sqlPreGenerateResult.getRelationTables() == null
                ? 0
                : sqlPreGenerateResult.getRelationTables().size();
        return "result evaluation passed"
                + (StringUtils.hasText(mainTable) ? ", mainTable=" + mainTable : "")
                + ", relationTableCount=" + relationTableCount;
    }

    private NodeResult evaluateByAi(ConversationRuntimeContext context, SqlPreGenerateResult sqlPreGenerateResult) {
        try {
            WorkflowEvaluationResponse response = workflowResultEvaluateService.evaluate(context);
            if (response == null) {
                return passWithFallback(context, sqlPreGenerateResult, "result evaluation ai response is null");
            }
            context.put(ConversationRuntimeContextKeys.Evaluation.RESULT_EVALUATION_RESPONSE, response);
            context.put(ConversationRuntimeContextKeys.Evaluation.RESULT_EVALUATION_SUMMARY, response.getReason());
            context.putNodeOutput(WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(), "evaluationResponse", response);
            context.putNodeOutput(WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(), "evaluationSummary", response.getReason());
            context.putNodeOutput(WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(), "blocking", !Boolean.TRUE.equals(response.getPassed()));
            if (Boolean.TRUE.equals(response.getPassed())) {
                context.getOrCreateNodeResult(WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode()).setStatus(STATUS_SUCCESS);
                context.publishProgressEvent(
                        ConversationEventSources.EVALUATION,
                        ConversationEventPhases.READY,
                        response.getReason()
                );
                return NodeResult.success(WorkflowNodeCodes.RENDER.getNodeCode());
            }
            markFailed(context, response.getReason());
            if (Boolean.TRUE.equals(response.getClarificationNeeded()) && StringUtils.hasText(response.getClarificationQuestion())) {
                context.publishClarificationEvent(
                        ConversationEventSources.EVALUATION,
                        ConversationEventPhases.READY,
                        response.getClarificationQuestion()
                );
            }
            return NodeResult.success(response.getRetryNodeCode());
        } catch (Exception ex) {
            log.warn("result evaluate ai failed, fallback to programmatic pass", ex);
            context.put(ConversationRuntimeContextKeys.Evaluation.RESULT_EVALUATION_ERROR, ex.getMessage());
            return passWithFallback(context, sqlPreGenerateResult, "result evaluation ai skipped: " + ex.getMessage());
        }
    }

    private NodeResult passWithFallback(ConversationRuntimeContext context,
                                        SqlPreGenerateResult sqlPreGenerateResult,
                                        String reason) {
        String summary = buildSuccessSummary(sqlPreGenerateResult);
        context.getOrCreateNodeResult(WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode()).setStatus(STATUS_SUCCESS);
        context.putNodeOutput(WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(), "evaluationSummary", summary);
        context.putNodeOutput(WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(), "blocking", Boolean.FALSE);
        context.publishProgressEvent(
                ConversationEventSources.EVALUATION,
                ConversationEventPhases.SKIPPED,
                reason
        );
        context.publishProgressEvent(
                ConversationEventSources.EVALUATION,
                ConversationEventPhases.READY,
                summary
        );
        return NodeResult.success(WorkflowNodeCodes.RENDER.getNodeCode());
    }

    private void markFailed(ConversationRuntimeContext context, String summary) {
        context.getOrCreateNodeResult(WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode()).setStatus(STATUS_FAILED);
        context.putNodeOutput(WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(), "evaluationSummary", summary);
        context.putNodeOutput(WorkflowNodeCodes.RESULT_EVALUATE.getNodeCode(), "blocking", Boolean.TRUE);
        context.publishProgressEvent(
                ConversationEventSources.EVALUATION,
                ConversationEventPhases.FAILED,
                summary
        );
    }
}
