package ai.platform.aiassit.chat.core.workflow.planning.skill.impl;

import ai.platform.aiassist.service.ai.api.AiRetrievalExecutionApi;
import ai.platform.aiassist.service.ai.api.dto.HybridSearchHit;
import ai.platform.aiassist.service.ai.api.dto.HybridSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.HybridSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.query.dto.AiChatToolDTO;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.planning.contract.IntentEvidence;
import ai.platform.aiassit.chat.core.workflow.planning.skill.QueryPlanningSkill;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于关键词的意图补全技能。
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Component
public class KeywordRetrievalPlanningSkill implements QueryPlanningSkill {

    private final AiRetrievalExecutionApi aiRetrievalExecutionApi;

    public KeywordRetrievalPlanningSkill(AiRetrievalExecutionApi aiRetrievalExecutionApi) {
        this.aiRetrievalExecutionApi = aiRetrievalExecutionApi;
    }

    @Override
    public String code() {
        return "query_planning_keyword_retrieval";
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
        String kbId = resolveKnowledgeBaseId(command);
        if (!StringUtils.hasText(kbId)) {
            return null;
        }
        HybridSearchResponse response = fetchKeywordHits(command, kbId);
        context.put("keywordHybridSearchResponse", response);
        context.put("keywordHybridSearchSummary", summarizeHits(response));

        IntentEvidence evidence = new IntentEvidence();
        evidence.setSource(code());
        evidence.setSummary("通过 retrieval API 执行关键词检索召回业务术语和候选数据集。");
        evidence.setScore(topScore(response));
        evidence.setTerms(resolveTerms(message, context.get("resolvedBusinessTerms"), response));
        evidence.setCandidateDatasets(resolveCandidateDatasets(response));
        evidence.setRequiredContext(resolveRequiredContext(response));
        evidence.getAttributes().put("kbId", kbId);
        evidence.getAttributes().put("retrievalMode", response == null ? null : response.getRetrievalMode());
        return evidence;
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveTerms(String message, Object resolvedTerms, HybridSearchResponse response) {
        Set<String> terms = new LinkedHashSet<>();
        if (resolvedTerms instanceof List<?> items && !CollectionUtils.isEmpty(items)) {
            for (Object item : items) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    terms.add(String.valueOf(item).trim());
                }
            }
        }
        for (String part : message.split("[，,。；;：:\\s]+")) {
            if (StringUtils.hasText(part) && part.trim().length() >= 2) {
                terms.add(part.trim());
            }
            if (terms.size() >= 8) {
                break;
            }
        }
        if (response != null && !CollectionUtils.isEmpty(response.getHits())) {
            for (HybridSearchHit hit : response.getHits()) {
                collectTerms(terms, hit == null ? null : hit.getContent());
                if (terms.size() >= 12) {
                    break;
                }
            }
        }
        return new ArrayList<>(terms);
    }

    private void collectTerms(Set<String> terms, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        for (String part : text.split("[，,。；;：:\\s]+")) {
            if (StringUtils.hasText(part) && part.trim().length() >= 2) {
                terms.add(part.trim());
            }
            if (terms.size() >= 12) {
                return;
            }
        }
    }

    private List<String> resolveCandidateDatasets(HybridSearchResponse response) {
        Set<String> datasets = new LinkedHashSet<>();
        if (response == null || CollectionUtils.isEmpty(response.getHits())) {
            return new ArrayList<>(datasets);
        }
        for (HybridSearchHit hit : response.getHits()) {
            if (hit == null || hit.getMetadata() == null) {
                continue;
            }
            addIfText(datasets, hit.getMetadata().get("dataset"));
            addIfText(datasets, hit.getMetadata().get("datasetCode"));
            addIfText(datasets, hit.getMetadata().get("table"));
        }
        return new ArrayList<>(datasets);
    }

    private void addIfText(Set<String> values, Object value) {
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            values.add(String.valueOf(value).trim());
        }
    }

    private List<String> resolveRequiredContext(HybridSearchResponse response) {
        List<String> requiredContext = new ArrayList<>();
        if (response == null || CollectionUtils.isEmpty(response.getHits())) {
            return requiredContext;
        }
        requiredContext.add("优先阅读关键词命中的知识片段，确认术语口径和字段定义。");
        List<String> datasets = resolveCandidateDatasets(response);
        if (!datasets.isEmpty()) {
            requiredContext.add("优先核对数据集：" + String.join("、", datasets));
        }
        String summary = summarizeHits(response);
        if (StringUtils.hasText(summary)) {
            requiredContext.add("关键词检索摘要：" + summary);
        }
        return requiredContext;
    }

    private HybridSearchResponse fetchKeywordHits(AiChatQueryCommand command, String kbId) {
        HybridSearchRequest request = new HybridSearchRequest();
        request.setProvider(null);
        request.setKbId(kbId);
        request.setQuery(command.getMessage());
        request.setKeywordEnabled(Boolean.TRUE);
        request.setVectorEnabled(Boolean.FALSE);
        request.setRerankEnabled(Boolean.FALSE);
        request.setTopK(resolveTopK(command, "keywordTopK", 5));
        request.setKeywordTopK(resolveTopK(command, "keywordTopK", 5));
        request.setMeta(buildMeta(command, "ai-chat-query-planning-keyword"));
        return aiRetrievalExecutionApi.hybridSearch(request);
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
