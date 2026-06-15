package ai.platform.aiassit.chat.core.workflow.planning.skill;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.planning.contract.IntentAnalysisBundle;
import ai.platform.aiassit.chat.core.workflow.planning.contract.IntentEvidence;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 查询规划技能执行器。
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Component
public class QueryPlanningSkillExecutor {

    private final List<QueryPlanningSkill> skills;

    public QueryPlanningSkillExecutor(List<QueryPlanningSkill> skills) {
        this.skills = skills.stream()
                .sorted(Comparator.comparingInt(QueryPlanningSkill::order))
                .toList();
    }

    public IntentAnalysisBundle analyze(WorkflowContext context) {
        IntentAnalysisBundle bundle = new IntentAnalysisBundle();
        AiChatQueryCommand command = context.getCommand();
        bundle.setOriginalQuery(command == null ? null : command.getMessage());

        List<IntentEvidence> evidences = new ArrayList<>();
        for (QueryPlanningSkill skill : skills) {
            IntentEvidence evidence = skill.analyze(context);
            if (evidence == null) {
                continue;
            }
            evidences.add(evidence);
            merge(bundle, evidence);
        }
        bundle.setEvidences(evidences);
        if (!StringUtils.hasText(bundle.getRewrittenQuery())) {
            bundle.setRewrittenQuery(bundle.getOriginalQuery());
        }
        if (bundle.getConfidence() == null) {
            bundle.setConfidence(calculateConfidence(evidences));
        }
        if (bundle.getClarificationNeeded() == null) {
            bundle.setClarificationNeeded(Boolean.FALSE);
        }
        return bundle;
    }

    private void merge(IntentAnalysisBundle bundle, IntentEvidence evidence) {
        if (!StringUtils.hasText(bundle.getIntentType()) && StringUtils.hasText(evidence.getIntentType())) {
            bundle.setIntentType(evidence.getIntentType().trim());
        }
        if (!StringUtils.hasText(bundle.getRewrittenQuery()) && StringUtils.hasText(evidence.getRewrittenQuery())) {
            bundle.setRewrittenQuery(evidence.getRewrittenQuery().trim());
        }
        mergeDistinct(bundle.getIntentLabels(), evidence.getIntentLabels());
        mergeDistinct(bundle.getTerms(), evidence.getTerms());
        mergeDistinct(bundle.getMetrics(), evidence.getMetrics());
        mergeDistinct(bundle.getDimensions(), evidence.getDimensions());
        mergeDistinct(bundle.getCandidateDatasets(), evidence.getCandidateDatasets());
        mergeDistinct(bundle.getRequiredContext(), evidence.getRequiredContext());
        mergeDistinct(bundle.getRisks(), evidence.getRisks());
        mergeDistinct(bundle.getClarificationQuestions(), evidence.getClarificationQuestions());
        if (bundle.getTimeRange().isEmpty() && evidence.getTimeRange() != null && !evidence.getTimeRange().isEmpty()) {
            bundle.setTimeRange(new java.util.LinkedHashMap<>(evidence.getTimeRange()));
        }
        if (Boolean.TRUE.equals(evidence.getClarificationNeeded())) {
            bundle.setClarificationNeeded(Boolean.TRUE);
        }
        if (evidence.getScore() != null) {
            bundle.setConfidence(Math.max(bundle.getConfidence() == null ? 0D : bundle.getConfidence(), evidence.getScore()));
        }
    }

    private void mergeDistinct(List<String> target, List<String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        Set<String> values = new LinkedHashSet<>(target);
        for (String item : source) {
            if (StringUtils.hasText(item)) {
                values.add(item.trim());
            }
        }
        target.clear();
        target.addAll(values);
    }

    private Double calculateConfidence(List<IntentEvidence> evidences) {
        return evidences.stream()
                .map(IntentEvidence::getScore)
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(0D);
    }
}
