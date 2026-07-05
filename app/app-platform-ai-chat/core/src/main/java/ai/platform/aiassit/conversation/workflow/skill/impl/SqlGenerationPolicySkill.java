package ai.platform.aiassit.conversation.workflow.skill.impl;

import ai.platform.aiassit.conversation.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.conversation.workflow.bean.NodeResult;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowSkillPhase;
import ai.platform.aiassit.conversation.workflow.context.WorkflowContext;
import ai.platform.aiassit.conversation.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.conversation.workflow.skill.IWorkflowNodeSkill;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SQL 生成规范技能。
 *
 * <p>在 SQL 生成节点执行前收敛硬约束和软规范，避免模型仅依赖自由提示生成 SQL。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Component
public class SqlGenerationPolicySkill implements IWorkflowNodeSkill {

    @Override
    public String code() {
        return "sql_generation_policy";
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
        Map<String, Object> policy = buildPolicy(command, context);
        context.put(WorkflowContextKeys.Skill.SQL_GENERATION_POLICY, policy);
        return NodeResult.success(nodeResult == null ? null : nodeResult.getNextNodeId());
    }

    private Map<String, Object> buildPolicy(AiChatQueryCommand command, WorkflowContext context) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("hardConstraints", buildHardConstraints(command, context));
        policy.put("softGuidelines", buildSoftGuidelines(command, context));
        policy.put("sources", buildSources(command, context));
        return policy;
    }

    private List<String> buildHardConstraints(AiChatQueryCommand command, WorkflowContext context) {
        Set<String> constraints = new LinkedHashSet<>();
        constraints.add("只允许生成单条 SELECT 或 WITH 查询。");
        constraints.add("禁止生成 INSERT、UPDATE、DELETE、DROP、ALTER、TRUNCATE、CREATE、MERGE、GRANT、REVOKE。");
        constraints.add("禁止生成多条 SQL 语句。");
        constraints.add("优先显式列名，避免使用 SELECT *。");
        constraints.add("若知识上下文或规划中已给出指标、维度、表或字段定义，必须优先沿用，不要自造口径。");
        constraints.add("当存在时间条件时，优先使用明确时间范围，不要省略时间过滤。");
        constraints.add("若涉及聚合指标，必须保证聚合表达式与 GROUP BY 维度一致。");
        constraints.add("仅在当前上下文能支持时使用 JOIN，无法确认关联关系时不要臆造关联字段。");

        List<String> businessTerms = context.get(WorkflowContextKeys.Skill.RESOLVED_BUSINESS_TERMS);
        if (!CollectionUtils.isEmpty(businessTerms)) {
            constraints.add("术语解析结果已提供业务术语，请优先使用这些标准术语对应的字段和口径：" + businessTerms);
        }
        Object normalizedTimeRange = context.get(WorkflowContextKeys.Skill.NORMALIZED_TIME_RANGE);
        if (normalizedTimeRange != null) {
            constraints.add("已识别标准化时间范围，请在 SQL 条件中优先落实该时间范围：" + normalizedTimeRange);
        }
        if (StringUtils.hasText(context.getKnowledgeResult())) {
            constraints.add("知识上下文已提供补充说明，若其中包含表、字段、指标、过滤口径，必须优先遵守。");
        }

        appendCustomRules(constraints, command.getExt(), "sqlGenerationPolicy");
        appendCustomRules(constraints, command.getExt(), "sqlPolicy");
        appendCustomRules(constraints, command.getExt(), "hardConstraints");
        return new ArrayList<>(constraints);
    }

    private List<String> buildSoftGuidelines(AiChatQueryCommand command, WorkflowContext context) {
        Set<String> guidelines = new LinkedHashSet<>();
        guidelines.add("在满足硬约束前提下，优先生成可读性高、结构稳定的 SQL。");
        guidelines.add("若规划中存在核心指标优先级，排序优先围绕核心指标展开。");
        guidelines.add("若信息不足，可使用单行 SQL 注释声明关键假设，但不要输出解释性正文。");
        guidelines.add("若历史上下文已出现稳定分析维度，可在不冲突时沿用。");

        if (StringUtils.hasText(context.getAnalysisResult())) {
            guidelines.add("优先覆盖查询规划中明确列出的分析维度、过滤条件和风险提示。");
        }

        appendCustomRules(guidelines, command.getExt(), "sqlSoftGuidelines");
        appendCustomRules(guidelines, command.getExt(), "softGuidelines");
        return new ArrayList<>(guidelines);
    }

    private List<String> buildSources(AiChatQueryCommand command, WorkflowContext context) {
        List<String> sources = new ArrayList<>();
        sources.add("built-in-default-policy");
        if (context.get(WorkflowContextKeys.Skill.RESOLVED_BUSINESS_TERMS) != null) {
            sources.add("resolvedBusinessTerms");
        }
        if (context.get(WorkflowContextKeys.Skill.NORMALIZED_TIME_RANGE) != null) {
            sources.add("normalizedTimeRange");
        }
        if (StringUtils.hasText(context.getKnowledgeResult())) {
            sources.add("knowledgeResult");
        }
        if (containsAnyKey(command.getExt(), "sqlGenerationPolicy", "sqlPolicy", "hardConstraints", "sqlSoftGuidelines", "softGuidelines")) {
            sources.add("command.ext");
        }
        return sources;
    }

    private void appendCustomRules(Set<String> target, Map<String, Object> ext, String key) {
        if (ext == null || !ext.containsKey(key)) {
            return;
        }
        Object value = ext.get(key);
        if (value instanceof String str && StringUtils.hasText(str)) {
            target.add(str.trim());
            return;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    target.add(String.valueOf(item).trim());
                }
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            Object hardConstraints = map.get("hardConstraints");
            if (hardConstraints instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null && StringUtils.hasText(String.valueOf(item))) {
                        target.add(String.valueOf(item).trim());
                    }
                }
            }
        }
    }

    private boolean containsAnyKey(Map<String, Object> ext, String... keys) {
        if (ext == null) {
            return false;
        }
        for (String key : keys) {
            if (ext.containsKey(key)) {
                return true;
            }
        }
        return false;
    }
}
