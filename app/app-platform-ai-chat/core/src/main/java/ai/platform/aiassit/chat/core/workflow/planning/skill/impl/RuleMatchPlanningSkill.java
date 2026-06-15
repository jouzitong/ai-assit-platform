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
    public IntentEvidence analyze(WorkflowContext context) {
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
        return evidence;
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
        if (CollectionUtils.isEmpty(context.getSessionMessages())) {
            return List.of();
        }
        return context.getSessionMessages().stream()
                .filter(Objects::nonNull)
                .filter(message -> context.getCurrentUserMessage() == null
                        || !Objects.equals(message.getMessageCode(), context.getCurrentUserMessage().getMessageCode()))
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
