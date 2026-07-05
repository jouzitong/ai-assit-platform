package ai.platform.aiassit.conversation.workflow.skill.impl;

import ai.platform.aiassit.conversation.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.conversation.workflow.bean.NodeResult;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowSkillPhase;
import ai.platform.aiassit.conversation.workflow.context.WorkflowContext;
import ai.platform.aiassit.conversation.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.conversation.workflow.skill.IWorkflowNodeSkill;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 时间范围归一化技能。
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
//@Component
public class TimeRangeNormalizeSkill implements IWorkflowNodeSkill {

    @Override
    public String code() {
        return "time_range_normalize";
    }

    @Override
    public WorkflowSkillPhase phase() {
        return WorkflowSkillPhase.BEFORE_EXECUTE;
    }

    @Override
    public NodeResult execute(WorkflowContext context, WorkflowNodeConfig nodeConfig, NodeResult nodeResult) {
        AiChatQueryCommand command = context.getCommand();
        if (command == null) {
            return NodeResult.fail("command is required");
        }
        Map<String, Object> normalizedRange = resolveRange(command);
        if (!normalizedRange.isEmpty()) {
            context.put(WorkflowContextKeys.Skill.NORMALIZED_TIME_RANGE, normalizedRange);
        }
        return NodeResult.success(nodeResult == null ? null : nodeResult.getNextNodeId());
    }

    private Map<String, Object> resolveRange(AiChatQueryCommand command) {
        LocalDate today = LocalDate.now();
        String message = command.getMessage();
        Map<String, Object> range = new LinkedHashMap<>();
        if (!StringUtils.hasText(message)) {
            return range;
        }
        String normalizedMessage = message.trim();
        if (normalizedMessage.contains("今天")) {
            range.put("granularity", "DAY");
            range.put("startDate", today.toString());
            range.put("endDate", today.toString());
            return range;
        }
        if (normalizedMessage.contains("昨天")) {
            LocalDate yesterday = today.minusDays(1);
            range.put("granularity", "DAY");
            range.put("startDate", yesterday.toString());
            range.put("endDate", yesterday.toString());
            return range;
        }
        if (normalizedMessage.contains("本月") || normalizedMessage.contains("这个月")) {
            range.put("granularity", "MONTH");
            range.put("startDate", today.withDayOfMonth(1).toString());
            range.put("endDate", today.toString());
            return range;
        }
        if (normalizedMessage.contains("上月") || normalizedMessage.contains("上个月")) {
            LocalDate lastMonth = today.minusMonths(1);
            range.put("granularity", "MONTH");
            range.put("startDate", lastMonth.withDayOfMonth(1).toString());
            range.put("endDate", lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()).toString());
        }
        return range;
    }
}
