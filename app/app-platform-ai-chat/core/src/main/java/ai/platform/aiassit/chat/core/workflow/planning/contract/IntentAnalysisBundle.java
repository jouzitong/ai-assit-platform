package ai.platform.aiassit.chat.core.workflow.planning.contract;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聚合后的意图分析结果。
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Data
public class IntentAnalysisBundle {

    private String originalQuery;

    private String rewrittenQuery;

    private String intentType;

    private List<String> intentLabels = new ArrayList<>();

    private List<String> terms = new ArrayList<>();

    private List<String> metrics = new ArrayList<>();

    private List<String> dimensions = new ArrayList<>();

    private List<String> candidateDatasets = new ArrayList<>();

    private List<String> requiredContext = new ArrayList<>();

    private List<String> risks = new ArrayList<>();

    private Map<String, Object> timeRange = new LinkedHashMap<>();

    private Boolean clarificationNeeded = Boolean.FALSE;

    private List<String> clarificationQuestions = new ArrayList<>();

    private List<IntentEvidence> evidences = new ArrayList<>();

    private Double confidence;
}
