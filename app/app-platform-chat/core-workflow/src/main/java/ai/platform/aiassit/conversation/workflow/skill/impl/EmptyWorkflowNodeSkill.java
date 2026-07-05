package ai.platform.aiassit.conversation.workflow.skill.impl;

import ai.platform.aiassit.conversation.workflow.bean.NodeResult;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowSkillPhase;
import ai.platform.aiassit.conversation.workflow.context.WorkflowContext;
import ai.platform.aiassit.conversation.workflow.skill.IWorkflowNodeSkill;
import org.springframework.stereotype.Component;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Component
public class EmptyWorkflowNodeSkill implements IWorkflowNodeSkill {

    @Override
    public String code() {
        return "empty-test";
    }

    @Override
    public WorkflowSkillPhase phase() {
        return WorkflowSkillPhase.BEFORE_EXECUTE;
    }

    @Override
    public NodeResult execute(WorkflowContext context, WorkflowNodeConfig nodeConfig, NodeResult nodeResult) {
        return nodeResult;
    }
}
