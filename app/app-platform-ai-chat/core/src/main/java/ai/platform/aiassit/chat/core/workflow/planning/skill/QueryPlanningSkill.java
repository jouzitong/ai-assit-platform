package ai.platform.aiassit.chat.core.workflow.planning.skill;

import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.planning.contract.IntentEvidence;

/**
 * 查询规划专用技能接口。
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
public interface QueryPlanningSkill {

    String code();

    int order();

    IntentEvidence analyze(WorkflowContext context);
}
