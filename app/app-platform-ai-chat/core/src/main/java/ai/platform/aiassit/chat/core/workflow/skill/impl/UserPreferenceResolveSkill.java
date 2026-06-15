package ai.platform.aiassit.chat.core.workflow.skill.impl;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowSkillPhase;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.skill.IWorkflowNodeSkill;
import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 用户 SQL 习惯偏好解析技能。
 *
 * <p>在 SQL 生成前抽取对查询习惯有帮助的软偏好，但不把这些偏好提升为硬约束。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Component
public class UserPreferenceResolveSkill implements IWorkflowNodeSkill {

    private static final String CONTEXT_KEY = "resolvedUserPreferences";

    @Override
    public String code() {
        return "user_preference_resolve";
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
        Map<String, Object> preferenceProfile = resolvePreferenceProfile(command, context);
        if (!preferenceProfile.isEmpty()) {
            context.put(CONTEXT_KEY, preferenceProfile);
        }
        return NodeResult.success(nodeResult == null ? null : nodeResult.getNextNodeId());
    }

    private Map<String, Object> resolvePreferenceProfile(AiChatQueryCommand command, WorkflowContext context) {
        Map<String, Object> profile = new LinkedHashMap<>();

        Map<String, Object> explicitPreferences = extractExplicitPreferences(command.getExt());
        Map<String, Object> inferredPreferences = inferPreferences(context);
        List<String> evidences = buildEvidences(explicitPreferences, inferredPreferences);

        if (!explicitPreferences.isEmpty()) {
            profile.put("explicitPreferences", explicitPreferences);
        }
        if (!inferredPreferences.isEmpty()) {
            profile.put("softPreferences", inferredPreferences);
        }
        if (!evidences.isEmpty()) {
            profile.put("evidences", evidences);
        }
        if (!profile.isEmpty()) {
            profile.put("rule", "用户本轮明确要求优先于历史偏好；历史偏好仅作为 SQL 生成时的软参考。");
        }
        return profile;
    }

    private Map<String, Object> extractExplicitPreferences(Map<String, Object> ext) {
        Map<String, Object> preferences = new LinkedHashMap<>();
        if (ext == null || ext.isEmpty()) {
            return preferences;
        }
        copyIfPresent(preferences, ext, "userPreferences");
        copyIfPresent(preferences, ext, "preferences");
        copyIfPresent(preferences, ext, "sqlPreferences");
        copyIfPresent(preferences, ext, "defaultTimeGranularity");
        copyIfPresent(preferences, ext, "defaultDimension");
        copyIfPresent(preferences, ext, "defaultOrderBy");
        copyIfPresent(preferences, ext, "defaultFilters");
        return preferences;
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (!source.containsKey(key)) {
            return;
        }
        Object value = source.get(key);
        if (value instanceof String str && !StringUtils.hasText(str)) {
            return;
        }
        if (value instanceof List<?> list && CollectionUtils.isEmpty(list)) {
            return;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return;
        }
        target.put(key, value);
    }

    private Map<String, Object> inferPreferences(WorkflowContext context) {
        List<String> corpus = new ArrayList<>();
        if (!CollectionUtils.isEmpty(context.getSessionMessages())) {
            for (AiChatMessageDTO message : context.getSessionMessages()) {
                if (message != null && StringUtils.hasText(message.getContent())) {
                    corpus.add(message.getContent());
                }
            }
        }
        if (!CollectionUtils.isEmpty(context.getSessionArtifacts())) {
            for (AiChatArtifactDTO artifact : context.getSessionArtifacts()) {
                if (artifact != null && StringUtils.hasText(artifact.getContent())) {
                    corpus.add(artifact.getContent());
                }
            }
        }

        Map<String, Object> inferred = new LinkedHashMap<>();
        String timeGranularity = inferTimeGranularity(corpus);
        if (timeGranularity != null) {
            inferred.put("preferredTimeGranularity", timeGranularity);
        }

        List<String> dimensions = inferDimensions(corpus);
        if (!dimensions.isEmpty()) {
            inferred.put("preferredDimensions", dimensions);
        }

        List<String> filters = inferFilters(corpus);
        if (!filters.isEmpty()) {
            inferred.put("preferredFilters", filters);
        }
        return inferred;
    }

    private String inferTimeGranularity(List<String> corpus) {
        int dayScore = countMatches(corpus, "今天", "昨天", "每日", "按天", "day", "daily");
        int weekScore = countMatches(corpus, "本周", "上周", "每周", "按周", "week", "weekly");
        int monthScore = countMatches(corpus, "本月", "上月", "每月", "按月", "month", "monthly");
        int maxScore = Math.max(dayScore, Math.max(weekScore, monthScore));
        if (maxScore < 2) {
            return null;
        }
        if (maxScore == dayScore) {
            return "DAY";
        }
        if (maxScore == weekScore) {
            return "WEEK";
        }
        return "MONTH";
    }

    private List<String> inferDimensions(List<String> corpus) {
        Set<String> dimensions = new LinkedHashSet<>();
        if (countMatches(corpus, "国家", "地区", "country", "region") >= 2) {
            dimensions.add("country");
        }
        if (countMatches(corpus, "产品", "product", "币种", "symbol") >= 2) {
            dimensions.add("product");
        }
        if (countMatches(corpus, "渠道", "source", "channel") >= 2) {
            dimensions.add("channel");
        }
        if (countMatches(corpus, "用户", "客户", "user", "customer") >= 2) {
            dimensions.add("user");
        }
        return new ArrayList<>(dimensions);
    }

    private List<String> inferFilters(List<String> corpus) {
        Set<String> filters = new LinkedHashSet<>();
        if (countMatches(corpus, "有效订单", "有效", "valid order") >= 2) {
            filters.add("prefer_valid_orders_only");
        }
        if (countMatches(corpus, "测试数据", "测试单", "test data", "test order") >= 2) {
            filters.add("prefer_exclude_test_data");
        }
        if (countMatches(corpus, "top", "排名", "倒序", "降序") >= 2) {
            filters.add("prefer_desc_sort_for_topn");
        }
        return new ArrayList<>(filters);
    }

    private int countMatches(List<String> corpus, String... keywords) {
        int score = 0;
        for (String text : corpus) {
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String normalized = text.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                    score++;
                    break;
                }
            }
        }
        return score;
    }

    private List<String> buildEvidences(Map<String, Object> explicitPreferences, Map<String, Object> inferredPreferences) {
        List<String> evidences = new ArrayList<>();
        if (!explicitPreferences.isEmpty()) {
            evidences.add("command.ext 提供了显式用户偏好");
        }
        if (!inferredPreferences.isEmpty()) {
            evidences.add("会话历史消息或历史产物中存在重复出现的分析习惯");
        }
        return evidences;
    }
}
