package ai.platform.aiassit.conversation.workflow.skill;

import ai.platform.aiassit.conversation.workflow.bean.NodeResult;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowSkillPhase;
import ai.platform.aiassit.conversation.workflow.context.WorkflowContext;

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
