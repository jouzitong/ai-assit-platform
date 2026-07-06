package ai.platform.aiassit.conversation.workflow.skill.impl;

import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.workflow.bean.NodeResult;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowSkillPhase;
import ai.platform.aiassit.conversation.workflow.context.WorkflowContext;
import ai.platform.aiassit.conversation.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.conversation.workflow.skill.IWorkflowNodeSkill;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 术语解析技能。
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
//@Component
public class BusinessTermResolveSkill implements IWorkflowNodeSkill {

    private static final String EXT_KEY = "businessTerms";
    @Override
    public String code() {
        return "business_term_resolve";
    }

    @Override
    public WorkflowSkillPhase phase() {
        return WorkflowSkillPhase.BEFORE_EXECUTE;
    }

    @Override
    public NodeResult execute(WorkflowContext context, WorkflowNodeConfig nodeConfig, NodeResult nodeResult) {
        ConversationQueryCommand command = context.getCommand();
        if (command == null) {
            return NodeResult.fail("command is required");
        }
        List<String> resolvedTerms = resolveTerms(command);
        context.put(WorkflowContextKeys.Skill.RESOLVED_BUSINESS_TERMS, resolvedTerms);
        return NodeResult.success(nodeResult == null ? null : nodeResult.getNextNodeId());
    }

    private List<String> resolveTerms(ConversationQueryCommand command) {
        Object extValue = command.getExt() == null ? null : command.getExt().get(EXT_KEY);
        if (extValue instanceof List<?> list && !CollectionUtils.isEmpty(list)) {
            Set<String> normalizedTerms = new LinkedHashSet<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String value = item instanceof Map<?, ?> map
                        ? stringify(map.get("term"))
                        : String.valueOf(item);
                if (StringUtils.hasText(value)) {
                    normalizedTerms.add(value.trim());
                }
            }
            if (!normalizedTerms.isEmpty()) {
                return new ArrayList<>(normalizedTerms);
            }
        }
        return extractTermsFromMessage(command.getMessage());
    }

    private List<String> extractTermsFromMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String part : message.split("[，,。；;：:\\s]+")) {
            if (StringUtils.hasText(part) && part.trim().length() >= 2) {
                terms.add(part.trim());
            }
            if (terms.size() >= 5) {
                break;
            }
        }
        return new ArrayList<>(terms);
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
