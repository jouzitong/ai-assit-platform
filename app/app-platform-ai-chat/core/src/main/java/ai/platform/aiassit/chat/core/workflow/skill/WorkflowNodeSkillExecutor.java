package ai.platform.aiassit.chat.core.workflow.skill;

import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeSkillConfig;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowSkillPhase;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点技能执行器。
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Component
public class WorkflowNodeSkillExecutor {

    private final Map<String, IWorkflowNodeSkill> skillRegistry = new HashMap<>();

    public WorkflowNodeSkillExecutor(List<IWorkflowNodeSkill> skills) {
        for (IWorkflowNodeSkill skill : skills) {
            skillRegistry.put(buildKey(skill.code(), skill.phase()), skill);
        }
    }

    public NodeResult execute(WorkflowContext context,
                              WorkflowNodeConfig nodeConfig,
                              WorkflowSkillPhase phase,
                              NodeResult nodeResult) {
        NodeResult currentResult = nodeResult == null ? NodeResult.success(null) : nodeResult;
        if (nodeConfig == null || CollectionUtils.isEmpty(nodeConfig.getSkills())) {
            return currentResult;
        }
        for (WorkflowNodeSkillConfig skillConfig : nodeConfig.getSkills()) {
            if (skillConfig == null || skillConfig.getPhase() != phase || !StringUtils.hasText(skillConfig.getCode())) {
                continue;
            }
            IWorkflowNodeSkill skill = skillRegistry.get(buildKey(skillConfig.getCode(), phase));
            if (skill == null) {
                return NodeResult.fail("workflow skill not found: " + skillConfig.getCode() + "@" + phase);
            }
            NodeResult skillResult = skill.execute(context, nodeConfig, currentResult);
            if (skillResult != null && !skillResult.isSuccess()) {
                return skillResult;
            }
            if (skillResult != null) {
                currentResult = skillResult;
            }
        }
        return currentResult;
    }

    private String buildKey(String code, WorkflowSkillPhase phase) {
        return code.trim() + "@" + phase.name();
    }
}
