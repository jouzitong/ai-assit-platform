package ai.platform.aiassit.chat.core.workflow.planning.skill.impl;

import ai.platform.aiassist.service.ai.api.AiRetrievalExecutionApi;
import ai.platform.aiassist.service.ai.api.dto.HybridSearchHit;
import ai.platform.aiassist.service.ai.api.dto.HybridSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.HybridSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassist.service.ai.api.enums.MessageRole;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.query.dto.AiChatToolDTO;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.chat.core.workflow.planning.contract.IntentEvidence;
import ai.platform.aiassit.chat.core.workflow.planning.contract.PlanningContextMessage;
import ai.platform.aiassit.chat.core.workflow.planning.contract.QueryPlanningSkillResult;
import ai.platform.aiassit.chat.core.workflow.planning.skill.QueryPlanningSkill;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于语义近似的意图补全技能。
 *
 * <p>当前先用轻量规则模拟语义召回输出，后续可以替换成真实向量检索实现。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Component
public class VectorRetrievalPlanningSkill implements QueryPlanningSkill {

    private final AiRetrievalExecutionApi aiRetrievalExecutionApi;

    public VectorRetrievalPlanningSkill(AiRetrievalExecutionApi aiRetrievalExecutionApi) {
        this.aiRetrievalExecutionApi = aiRetrievalExecutionApi;
    }

    @Override
    public String code() {
        return "query_planning_vector_retrieval";
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public QueryPlanningSkillResult analyze(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        String message = command == null ? null : command.getMessage();
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String kbId = resolveKnowledgeBaseId(command);
        if (!StringUtils.hasText(kbId)) {
            return null;
        }
        HybridSearchResponse response = fetchVectorHits(command, context, kbId);
        context.put(WorkflowContextKeys.Planning.VECTOR_HYBRID_SEARCH_RESPONSE, response);
        context.put(WorkflowContextKeys.Planning.VECTOR_HYBRID_SEARCH_SUMMARY, summarizeHits(response));

        IntentEvidence evidence = new IntentEvidence();
        evidence.setSource(code());
        evidence.setSummary("通过 retrieval API 执行语义召回，补充相似问题和语义近邻上下文。");
        evidence.setScore(topScore(response));
        evidence.setRequiredContext(resolveSemanticContext(response));
        evidence.setRisks(resolveSemanticRisks(response));
        evidence.getAttributes().put("kbId", kbId);
        evidence.getAttributes().put("retrievalMode", response == null ? null : response.getRetrievalMode());
        QueryPlanningSkillResult result = new QueryPlanningSkillResult();
        result.setEvidence(evidence);
        result.getMessages().add(buildPlanningMessage(response, evidence));
        return result;
    }

    private PlanningContextMessage buildPlanningMessage(HybridSearchResponse response, IntentEvidence evidence) {
        PlanningContextMessage message = new PlanningContextMessage();
        message.setSource(code());
        message.setSection("语义召回说明");
        message.setRole(MessageRole.SYSTEM);
        message.setPriority(300);
        StringBuilder builder = new StringBuilder();
        builder.append("请把语义召回结果当作补充性知识，不要当成绝对事实。它更适合补充相似问题经验、潜在 scope、风险提示和 ambiguity。").append('\n');
        builder.append("填写规则：").append('\n');
        builder.append("1. 语义召回可帮助补充 ext、ambiguity、render、intent 的候选方向。").append('\n');
        builder.append("2. 如果语义召回与用户原话冲突，优先以用户原话和高置信规则识别为准。").append('\n');
        builder.append("3. 不要因为语义相似就编造主体、条件或统计口径。").append('\n');
        if (evidence != null) {
            if (!CollectionUtils.isEmpty(evidence.getRequiredContext())) {
                builder.append("语义补充上下文：").append(evidence.getRequiredContext()).append('\n');
            }
            if (!CollectionUtils.isEmpty(evidence.getRisks())) {
                builder.append("语义召回风险：").append(evidence.getRisks()).append('\n');
            }
            if (evidence.getScore() != null) {
                builder.append("本技能建议置信度：").append(evidence.getScore()).append('\n');
            }
        }
        String summary = summarizeHits(response);
        if (StringUtils.hasText(summary)) {
            builder.append("语义命中摘要：").append(summary).append('\n');
        }
        message.setContent(builder.toString().trim());
        return message;
    }

    private HybridSearchResponse fetchVectorHits(AiChatQueryCommand command, WorkflowContext context, String kbId) {
        HybridSearchRequest request = new HybridSearchRequest();
        request.setProvider(null);
        request.setKbId(kbId);
        request.setQuery(buildRetrievalQuery(command, context));
        request.setKeywordEnabled(Boolean.FALSE);
        request.setVectorEnabled(Boolean.TRUE);
        request.setRerankEnabled(Boolean.FALSE);
        request.setTopK(resolveTopK(command, "vectorTopK", 5));
        request.setVectorTopK(resolveTopK(command, "vectorTopK", 5));
        request.setMeta(buildMeta(command, "ai-chat-query-planning-vector"));
        return aiRetrievalExecutionApi.hybridSearch(request);
    }

    private String buildRetrievalQuery(AiChatQueryCommand command, WorkflowContext context) {
        String currentMessage = command == null ? null : command.getMessage();
        String messageSummary = context == null ? null : context.getOrCreateUserMessageContext().getSummary();
        if (!StringUtils.hasText(messageSummary)) {
            return currentMessage;
        }
        return """
                当前问题：
                %s

                用户消息上下文汇总：
                %s
                """.formatted(defaultText(currentMessage), messageSummary).trim();
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private List<String> resolveSemanticContext(HybridSearchResponse response) {
        List<String> context = new ArrayList<>();
        if (response == null || CollectionUtils.isEmpty(response.getHits())) {
            return context;
        }
        context.add("结合语义召回结果补充相似分析问题和关联知识片段。");
        String summary = summarizeHits(response);
        if (StringUtils.hasText(summary)) {
            context.add("语义召回摘要：" + summary);
        }
        return context;
    }

    private List<String> resolveSemanticRisks(HybridSearchResponse response) {
        List<String> risks = new ArrayList<>();
        if (response != null && Boolean.TRUE.equals(response.getDegraded())) {
            risks.add("语义召回已降级为基础知识检索，可能影响相似问题覆盖率。");
        }
        return risks;
    }

    private RequestMeta buildMeta(AiChatQueryCommand command, String defaultScene) {
        RequestMeta meta = new RequestMeta();
        if (command == null) {
            meta.setScene(defaultScene);
            return meta;
        }
        meta.setTraceId(command.getTraceId());
        meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() : defaultScene);
        meta.setExt(new java.util.HashMap<>(command.getExt()));
        return meta;
    }

    private Integer resolveTopK(AiChatQueryCommand command, String key, int defaultValue) {
        if (command != null && command.getExt() != null) {
            Object value = command.getExt().get(key);
            if (value instanceof Number number && number.intValue() > 0) {
                return number.intValue();
            }
        }
        return defaultValue;
    }

    private String resolveKnowledgeBaseId(AiChatQueryCommand command) {
        if (command == null) {
            return null;
        }
        Object extValue = command.getExt() == null ? null : command.getExt().get("kbId");
        if (extValue == null && command.getExt() != null) {
            extValue = command.getExt().get("knowledgeBaseId");
        }
        if (extValue instanceof String str && StringUtils.hasText(str)) {
            return str.trim();
        }
        if (!CollectionUtils.isEmpty(command.getTools())) {
            for (AiChatToolDTO tool : command.getTools()) {
                if (tool == null || tool.getExt() == null) {
                    continue;
                }
                Object kbId = tool.getExt().get("kbId");
                if (kbId == null) {
                    kbId = tool.getExt().get("knowledgeBaseId");
                }
                if (kbId instanceof String str && StringUtils.hasText(str)) {
                    return str.trim();
                }
            }
        }
        return null;
    }

    private Double topScore(HybridSearchResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getHits())) {
            return 0D;
        }
        return response.getHits().stream()
                .map(HybridSearchHit::getFinalScore)
                .filter(java.util.Objects::nonNull)
                .max(Double::compareTo)
                .orElse(0D);
    }

    private String summarizeHits(HybridSearchResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getHits())) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (HybridSearchHit hit : response.getHits()) {
            if (hit == null || !StringUtils.hasText(hit.getContent())) {
                continue;
            }
            String content = hit.getContent().trim();
            parts.add(content.length() > 60 ? content.substring(0, 60) : content);
            if (parts.size() >= 3) {
                break;
            }
        }
        return String.join(" | ", parts);
    }
}
