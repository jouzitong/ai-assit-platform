package ai.platform.aiassit.chat.core.workflow.skill.impl;

import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowSkillPhase;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.skill.IWorkflowNodeSkill;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 查询规划审查技能。
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
//@Component
public class QueryPlanReviewSkill implements IWorkflowNodeSkill {

    private static final String REVIEW_KEY = "queryPlanReview";

    @Override
    public String code() {
        return "query_plan_review";
    }

    @Override
    public WorkflowSkillPhase phase() {
        return WorkflowSkillPhase.REVIEW_OUTPUT;
    }

    @Override
    public NodeResult execute(WorkflowContext context, WorkflowNodeConfig nodeConfig, NodeResult nodeResult) {
        String analysisResult = context.getAnalysisResult();
        if (!StringUtils.hasText(analysisResult)) {
            return NodeResult.fail("analysisResult is required for query plan review");
        }
        String review = buildReview(analysisResult);
        context.put(REVIEW_KEY, review);
        return NodeResult.success(nodeResult == null ? null : nodeResult.getNextNodeId());
    }

    private String buildReview(String analysisResult) {
        StringBuilder builder = new StringBuilder("查询规划审查：");
        if (analysisResult.contains("风险") || analysisResult.contains("不足")) {
            builder.append("规划已标记信息不足或风险点，可进入知识检索补充上下文。");
        } else {
            builder.append("规划结构完整，可继续进入知识检索和 SQL 生成阶段。");
        }
        if (analysisResult.length() > 200) {
            builder.append(" 规划文本较长，后续节点可优先提取关键过滤条件与聚合口径。");
        }
        return builder.toString();
    }
}
