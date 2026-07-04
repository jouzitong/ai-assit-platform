package ai.platform.aiassit.chat.core.workflow.planning.skill.impl;

import ai.platform.aiassit.service.ai.api.dto.IntentAnalyzeResponse;
import ai.platform.aiassit.service.ai.api.enums.MessageRole;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.planning.contract.IntentEvidence;
import ai.platform.aiassit.chat.core.workflow.planning.contract.PlanningContextMessage;
import ai.platform.aiassit.chat.core.workflow.planning.contract.QueryPlanningSkillResult;
import ai.platform.aiassit.chat.core.workflow.planning.service.WorkflowIntentAnalyzeService;
import ai.platform.aiassit.chat.core.workflow.planning.skill.QueryPlanningSkill;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 基于规则的意图识别技能。
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Component
public class RuleMatchPlanningSkill implements QueryPlanningSkill {

    private final WorkflowIntentAnalyzeService workflowIntentAnalyzeService;

    public RuleMatchPlanningSkill(WorkflowIntentAnalyzeService workflowIntentAnalyzeService) {
        this.workflowIntentAnalyzeService = workflowIntentAnalyzeService;
    }

    @Override
    public String code() {
        return "query_planning_rule_match";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public QueryPlanningSkillResult analyze(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        String message = command == null ? null : command.getMessage();
        if (!StringUtils.hasText(message)) {
            return null;
        }
        IntentAnalyzeResponse response = context.get(WorkflowContextKeys.Planning.INTENT_ANALYZE_RESPONSE);
        if (response == null) {
            response = workflowIntentAnalyzeService.analyze(context);
            context.put(WorkflowContextKeys.Planning.INTENT_ANALYZE_RESPONSE, response);
        }
        context.put(WorkflowContextKeys.Planning.INTENT_ANALYZE_RESPONSE, response);

        IntentEvidence evidence = new IntentEvidence();
        evidence.setSource(code());
        evidence.setSummary(response.getSummary());
        evidence.setScore(0.95D);
        evidence.getAttributes().put("businessType", response.getBusinessType());
        evidence.setIntentType(response.getIntentType());
        evidence.setIntentLabels(response.getIntentLabels());
        evidence.setMetrics(response.getMetrics());
        evidence.setDimensions(response.getDimensions());
        evidence.setCandidateDatasets(response.getCandidateDatasets());
        evidence.setRequiredContext(response.getRequiredContext());
        evidence.setRisks(response.getRisks());
        evidence.setClarificationNeeded(response.getClarificationNeeded());
        evidence.setClarificationQuestions(response.getClarificationQuestions());
        evidence.setRewrittenQuery(response.getRewrittenQuery());
        evidence.setTimeRange(response.getTimeRange() == null ? Map.of() : response.getTimeRange());
        evidence.getAttributes().put("importantInfos", response.getImportantInfos());
        evidence.getAttributes().put("requestId", response.getRequestId());
        evidence.getAttributes().put("model", response.getModel());
        QueryPlanningSkillResult result = new QueryPlanningSkillResult();
        result.setEvidence(evidence);
        result.getMessages().add(buildPlanningMessage(response, evidence));
        return result;
    }

    private PlanningContextMessage buildPlanningMessage(IntentAnalyzeResponse response, IntentEvidence evidence) {
        PlanningContextMessage message = new PlanningContextMessage();
        message.setSource(code());
        message.setSection("意图识别说明");
        message.setRole(MessageRole.SYSTEM);
        message.setPriority(100);
        StringBuilder builder = new StringBuilder();
        builder.append("请优先依据本技能的结论补充 PlanningResult 中的 intent、subject、filters、ambiguity 字段。").append('\n');
        builder.append("意图填写规则：").append('\n');
        builder.append("1. intent.type 用英文标识，可多个值，用英文逗号分隔。").append('\n');
        builder.append("2. intent.name 用中文概括用户真正想完成的业务动作。").append('\n');
        builder.append("3. intent.action 用可执行动作描述，可多个值，用英文逗号分隔。").append('\n');
        builder.append("4. 如果识别结果存在明显歧义，不要强行定高分，应在 ambiguity 中补充问题。").append('\n');
        if (response != null) {
            if (StringUtils.hasText(response.getBusinessType())) {
                builder.append("识别业务类型：").append(response.getBusinessType()).append('\n');
            }
            if (StringUtils.hasText(response.getIntentType())) {
                builder.append("识别意图类型：").append(response.getIntentType()).append('\n');
            }
            if (StringUtils.hasText(response.getSummary())) {
                builder.append("用户需求摘要：").append(response.getSummary()).append('\n');
            }
            if (!CollectionUtils.isEmpty(response.getIntentLabels())) {
                builder.append("识别意图标签：").append(response.getIntentLabels()).append('\n');
            }
            if (StringUtils.hasText(response.getRewrittenQuery())) {
                builder.append("推荐改写问题：").append(response.getRewrittenQuery()).append('\n');
            }
            if (!CollectionUtils.isEmpty(response.getImportantInfos())) {
                builder.append("其他重要信息：").append(response.getImportantInfos()).append('\n');
            }
            if (!CollectionUtils.isEmpty(response.getClarificationQuestions())) {
                builder.append("建议澄清问题：").append(response.getClarificationQuestions()).append('\n');
            }
        }
        if (evidence != null && evidence.getScore() != null) {
            builder.append("本技能建议置信度：").append(evidence.getScore()).append('\n');
        }
        message.setContent(builder.toString().trim());
        return message;
    }
}
