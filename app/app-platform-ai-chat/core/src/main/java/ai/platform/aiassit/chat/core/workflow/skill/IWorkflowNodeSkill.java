package ai.platform.aiassit.chat.core.workflow.skill;

import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowSkillPhase;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;

/**
 * 节点技能接口。
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
public interface IWorkflowNodeSkill {

    String code();

    WorkflowSkillPhase phase();

    NodeResult execute(WorkflowContext context, WorkflowNodeConfig nodeConfig, NodeResult nodeResult);
}
