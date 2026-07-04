package ai.platform.aiassit.chat.core.workflow.planning.skill;

import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.planning.contract.QueryPlanningSkillResult;

/**
 * 查询规划专用技能接口。
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
public interface QueryPlanningSkill {

    String code();

    int order();

    QueryPlanningSkillResult analyze(WorkflowContext context);
}
