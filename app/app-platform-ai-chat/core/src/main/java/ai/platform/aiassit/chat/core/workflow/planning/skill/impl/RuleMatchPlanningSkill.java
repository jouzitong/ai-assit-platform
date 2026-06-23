package ai.platform.aiassit.chat.core.workflow.planning.skill.impl;

import ai.platform.aiassist.service.ai.api.AiRetrievalExecutionApi;
import ai.platform.aiassist.service.ai.api.dto.ChatMessage;
import ai.platform.aiassist.service.ai.api.dto.IntentAnalyzeRequest;
import ai.platform.aiassist.service.ai.api.dto.IntentAnalyzeResponse;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassist.service.ai.api.enums.MessageRole;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.planning.contract.IntentEvidence;
import ai.platform.aiassit.chat.core.workflow.planning.contract.PlanningContextMessage;
import ai.platform.aiassit.chat.core.workflow.planning.contract.QueryPlanningSkillResult;
import ai.platform.aiassit.chat.core.workflow.planning.skill.QueryPlanningSkill;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于规则的意图识别技能。
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Component
public class RuleMatchPlanningSkill implements QueryPlanningSkill {

    private final AiRetrievalExecutionApi aiRetrievalExecutionApi;

    public RuleMatchPlanningSkill(AiRetrievalExecutionApi aiRetrievalExecutionApi) {
        this.aiRetrievalExecutionApi = aiRetrievalExecutionApi;
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
        IntentAnalyzeResponse response = aiRetrievalExecutionApi.analyzeIntent(buildRequest(context));
        context.put("intentAnalyzeResponse", response);

        IntentEvidence evidence = new IntentEvidence();
        evidence.setSource(code());
        evidence.setSummary(response.getSummary());
        evidence.setScore(0.95D);
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
            if (StringUtils.hasText(response.getIntentType())) {
                builder.append("识别意图类型：").append(response.getIntentType()).append('\n');
            }
            if (!CollectionUtils.isEmpty(response.getIntentLabels())) {
                builder.append("识别意图标签：").append(response.getIntentLabels()).append('\n');
            }
            if (StringUtils.hasText(response.getRewrittenQuery())) {
                builder.append("推荐改写问题：").append(response.getRewrittenQuery()).append('\n');
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

    private IntentAnalyzeRequest buildRequest(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        IntentAnalyzeRequest request = new IntentAnalyzeRequest();
        request.setProvider(null);
        request.setModel(command == null ? null : command.getApiModel());
        request.setScene(command == null ? "ai-chat-query-planning-intent" : command.getScene());
        request.setQuery(command == null ? null : command.getMessage());
        request.setHistory(buildHistory(context));
        request.setRetrievalContext(buildRetrievalContext(context));
        request.setMeta(buildMeta(command));
        if (command != null && command.getExt() != null) {
            request.setExt(new java.util.HashMap<>(command.getExt()));
        }
        return request;
    }

    private List<ChatMessage> buildHistory(WorkflowContext context) {
        List<AiChatMessageDTO> sessionMessages = context.getOrCreateUserMessageContext().getSessionMessages();
        AiChatMessageDTO currentMessage = context.getOrCreateUserMessageContext().getCurrentMessage();
        if (CollectionUtils.isEmpty(sessionMessages)) {
            return List.of();
        }
        return sessionMessages.stream()
                .filter(Objects::nonNull)
                .filter(message -> currentMessage == null
                        || !Objects.equals(message.getMessageCode(), currentMessage.getMessageCode()))
                .sorted(Comparator.comparing(AiChatMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .map(this::toChatMessage)
                .filter(Objects::nonNull)
                .toList();
    }

    private ChatMessage toChatMessage(AiChatMessageDTO messageDTO) {
        if (messageDTO == null || !StringUtils.hasText(messageDTO.getContent())) {
            return null;
        }
        ChatMessage message = new ChatMessage();
        message.setContent(messageDTO.getContent());
        message.setRole(resolveRole(messageDTO.getRole()));
        return message;
    }

    private MessageRole resolveRole(String role) {
        if (!StringUtils.hasText(role)) {
            return MessageRole.USER;
        }
        try {
            return MessageRole.valueOf(role.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ex) {
            return MessageRole.USER;
        }
    }

    private String buildRetrievalContext(WorkflowContext context) {
        List<String> parts = new ArrayList<>();
        String userMessageSummary = context.getOrCreateUserMessageContext().getSummary();
        if (StringUtils.hasText(userMessageSummary)) {
            parts.add("用户消息上下文汇总：\n" + userMessageSummary);
        }
        Object keywordSummary = context.get("keywordHybridSearchSummary");
        if (keywordSummary instanceof String value && StringUtils.hasText(value)) {
            parts.add("关键词检索摘要：" + value);
        }
        Object vectorSummary = context.get("vectorHybridSearchSummary");
        if (vectorSummary instanceof String value && StringUtils.hasText(value)) {
            parts.add("语义召回摘要：" + value);
        }
        Object normalizedTimeRange = context.get("normalizedTimeRange");
        if (normalizedTimeRange != null) {
            parts.add("时间范围补充：" + normalizedTimeRange);
        }
        List<String> resolvedTerms = context.get("resolvedBusinessTerms");
        if (!CollectionUtils.isEmpty(resolvedTerms)) {
            parts.add("业务术语补充：" + resolvedTerms);
        }
        return String.join("\n", parts);
    }

    private RequestMeta buildMeta(AiChatQueryCommand command) {
        RequestMeta meta = new RequestMeta();
        if (command == null) {
            meta.setScene("ai-chat-query-planning-intent");
            return meta;
        }
        meta.setTraceId(command.getTraceId());
        meta.setTenantId(command.getUserId() == null ? null : String.valueOf(command.getUserId()));
        meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() : "ai-chat-query-planning-intent");
        meta.setExt(new java.util.HashMap<>(command.getExt()));
        return meta;
    }
}
