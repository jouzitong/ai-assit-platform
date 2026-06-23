package ai.platform.aiassit.chat.core.workflow.capability.impl;

import ai.platform.aiassist.service.ai.api.AiKnowledgeApi;
import ai.platform.aiassist.service.ai.api.dto.KbSearchItem;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.query.dto.AiChatToolDTO;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeCapabilityConfig;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.chat.core.workflow.capability.PromptContextCapability;
import ai.platform.aiassit.chat.core.workflow.capability.PromptContextItem;
import ai.platform.aiassit.chat.core.workflow.capability.PromptContextResult;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.chat.core.workflow.planning.contract.PlanningResult;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于知识库检索补充 prompt 上下文。
 *
 * @author zhouzhitong
 * @since 2026/6/23
 */
@Component
public class KnowledgeRetrievePromptContextCapability implements PromptContextCapability {

    public static final String CODE = "knowledge_retrieve_prompt_context";
    private static final String QUERY_MODE_PLANNING_SUBJECT_RELATIONS = "planning_subject_relations";

    private final AiKnowledgeApi knowledgeApi;

    public KnowledgeRetrievePromptContextCapability(AiKnowledgeApi knowledgeApi) {
        this.knowledgeApi = knowledgeApi;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public PromptContextResult load(WorkflowContext context,
                                    WorkflowNodeConfig nodeConfig,
                                    WorkflowNodeCapabilityConfig capabilityConfig) {
        AiChatQueryCommand command = context.getCommand();
        if (command == null) {
            return empty();
        }
        String kbId = resolveKnowledgeBaseId(command, capabilityConfig);
        if (!StringUtils.hasText(kbId)) {
            return empty();
        }
        context.setKnowledgeBaseId(kbId);
        KbSearchRequest request = new KbSearchRequest();
        request.setKbId(kbId);
        request.setQuery(buildQuery(command, context, capabilityConfig));
        request.setTopK(resolveTopK(command, capabilityConfig));

        RequestMeta meta = new RequestMeta();
        meta.setTraceId(command.getTraceId());
        meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() : "ai-chat-prompt-context");
        request.setMeta(meta);

        KbSearchResponse response = knowledgeApi.kbSearch(request);
        context.put(WorkflowContextKeys.Capability.KNOWLEDGE_SEARCH_RESPONSE, response);
        String content = formatKnowledgeHits(response == null ? null : response.getItems());
        if (!StringUtils.hasText(content)) {
            return empty();
        }
        context.setKnowledgeResult(content);
        context.put(WorkflowContextKeys.Capability.KNOWLEDGE_RESULT, content);

        PromptContextItem item = new PromptContextItem();
        item.setTitle(resolveTitle(capabilityConfig, "知识库上下文"));
        item.setSource("knowledge-base:" + kbId);
        item.setContent(content);
        item.setPriority(resolvePriority(capabilityConfig, 100));
        item.getMetadata().put("kbId", kbId);
        item.getMetadata().put("topK", request.getTopK());

        PromptContextResult result = new PromptContextResult();
        result.getItems().add(item);
        return result;
    }

    private PromptContextResult empty() {
        return new PromptContextResult();
    }

    private String buildQuery(AiChatQueryCommand command,
                              WorkflowContext context,
                              WorkflowNodeCapabilityConfig capabilityConfig) {
        String queryMode = resolveOptionAsString(capabilityConfig, "queryMode");
        if (QUERY_MODE_PLANNING_SUBJECT_RELATIONS.equals(queryMode)) {
            String planningDrivenQuery = buildPlanningDrivenQuery(command, context);
            if (StringUtils.hasText(planningDrivenQuery)) {
                return planningDrivenQuery;
            }
        }
        Object queryTemplate = capabilityConfig == null || capabilityConfig.getOptions() == null
                ? null
                : capabilityConfig.getOptions().get("queryTemplate");
        String message = command.getMessage();
        String analysis = context.getAnalysisResult();
        if (queryTemplate instanceof String template && StringUtils.hasText(template)) {
            return template
                    .replace("{message}", message == null ? "" : message)
                    .replace("{analysis}", analysis == null ? "" : analysis)
                    .trim();
        }
        if (StringUtils.hasText(analysis)) {
            return analysis;
        }
        return message;
    }

    private String buildPlanningDrivenQuery(AiChatQueryCommand command, WorkflowContext context) {
        PlanningResult planningResult = context.get(WorkflowContextKeys.Planning.QUERY_PLAN_RESULT);
        if (planningResult == null || planningResult.getSubject() == null) {
            return null;
        }
        PlanningResult.Subject subject = planningResult.getSubject();
        Set<String> subjectTerms = new LinkedHashSet<>();
        appendTerm(subjectTerms, subject.getName());
        appendTerm(subjectTerms, subject.getValue());
        appendTerms(subjectTerms, subject.getAliases());

        List<String> relationLines = new ArrayList<>();
        if (!CollectionUtils.isEmpty(subject.getRelations())) {
            for (PlanningResult.RelationItem relation : subject.getRelations()) {
                if (relation == null) {
                    continue;
                }
                Set<String> relationTerms = new LinkedHashSet<>();
                appendTerm(relationTerms, relation.getName());
                appendTerms(relationTerms, relation.getValues());
                appendTerms(relationTerms, relation.getAliases());
                if (!relationTerms.isEmpty()) {
                    relationLines.add(String.join(" / ", relationTerms));
                }
            }
        }

        if (subjectTerms.isEmpty() && relationLines.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("请基于以下查询规划对象，检索完整的真实表信息、字段说明、表关系和 SQL 使用口径。");
        if (!subjectTerms.isEmpty()) {
            builder.append("\n主体对象：").append(String.join(" / ", subjectTerms));
        }
        if (!relationLines.isEmpty()) {
            builder.append("\n关联对象：");
            for (int i = 0; i < relationLines.size(); i++) {
                builder.append("\n").append(i + 1).append(". ").append(relationLines.get(i));
            }
        }
        if (StringUtils.hasText(command.getMessage())) {
            builder.append("\n用户问题：").append(command.getMessage().trim());
        }
        if (StringUtils.hasText(context.getAnalysisResult())) {
            builder.append("\n规划摘要：").append(context.getAnalysisResult().trim());
        }
        return builder.toString().trim();
    }

    private int resolveTopK(AiChatQueryCommand command, WorkflowNodeCapabilityConfig capabilityConfig) {
        Object value = capabilityConfig == null || capabilityConfig.getOptions() == null
                ? null
                : capabilityConfig.getOptions().get("topK");
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        value = safeGet(command.getExt(), "kbTopK");
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        return 5;
    }

    private String resolveKnowledgeBaseId(AiChatQueryCommand command, WorkflowNodeCapabilityConfig capabilityConfig) {
        Object extValue = capabilityConfig == null || capabilityConfig.getOptions() == null
                ? null
                : capabilityConfig.getOptions().get("kbId");
        if (extValue == null) {
            extValue = safeGet(command.getExt(), "kbId");
        }
        if (extValue == null) {
            extValue = safeGet(command.getExt(), "knowledgeBaseId");
        }
        if (extValue instanceof String str && StringUtils.hasText(str)) {
            return str.trim();
        }
        if (!CollectionUtils.isEmpty(command.getTools())) {
            for (AiChatToolDTO tool : command.getTools()) {
                if (tool == null || tool.getExt() == null) {
                    continue;
                }
                Object kbId = safeGet(tool.getExt(), "kbId");
                if (kbId == null) {
                    kbId = safeGet(tool.getExt(), "knowledgeBaseId");
                }
                if (kbId instanceof String str && StringUtils.hasText(str)) {
                    return str.trim();
                }
            }
        }
        return null;
    }

    private String formatKnowledgeHits(List<KbSearchItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            return "";
        }
        StringBuilder builder = new StringBuilder("以下内容来自知识库命中，请优先以这些表、字段、口径、规则为准：");
        int index = 1;
        for (KbSearchItem item : items) {
            if (item == null || !StringUtils.hasText(item.getContent())) {
                continue;
            }
            builder.append('\n')
                    .append(index++)
                    .append(". ")
                    .append(item.getContent().trim());
            if (!CollectionUtils.isEmpty(item.getMetadata())) {
                builder.append("\n   metadata=").append(item.getMetadata());
            }
        }
        return builder.toString();
    }

    private String resolveTitle(WorkflowNodeCapabilityConfig capabilityConfig, String fallback) {
        Object value = capabilityConfig == null || capabilityConfig.getOptions() == null
                ? null
                : capabilityConfig.getOptions().get("title");
        return value instanceof String str && StringUtils.hasText(str) ? str.trim() : fallback;
    }

    private int resolvePriority(WorkflowNodeCapabilityConfig capabilityConfig, int fallback) {
        Object value = capabilityConfig == null || capabilityConfig.getOptions() == null
                ? null
                : capabilityConfig.getOptions().get("priority");
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String resolveOptionAsString(WorkflowNodeCapabilityConfig capabilityConfig, String key) {
        Object value = capabilityConfig == null || capabilityConfig.getOptions() == null
                ? null
                : capabilityConfig.getOptions().get(key);
        return value instanceof String str && StringUtils.hasText(str) ? str.trim() : null;
    }

    private void appendTerm(Set<String> target, String value) {
        if (StringUtils.hasText(value)) {
            target.add(value.trim());
        }
    }

    private void appendTerms(Set<String> target, List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return;
        }
        for (String value : values) {
            appendTerm(target, value);
        }
    }

    private Object safeGet(Map<String, Object> map, String key) {
        return map == null ? null : map.get(key);
    }
}
