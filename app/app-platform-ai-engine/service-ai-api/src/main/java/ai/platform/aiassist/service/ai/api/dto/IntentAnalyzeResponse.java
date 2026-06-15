package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class IntentAnalyzeResponse implements Serializable {

    private String requestId;

    private String model;

    private String intentType;

    private String rewrittenQuery;

    private String summary;

    private List<String> intentLabels = new ArrayList<>();

    private List<String> metrics = new ArrayList<>();

    private List<String> dimensions = new ArrayList<>();

    private List<String> candidateDatasets = new ArrayList<>();

    private List<String> requiredContext = new ArrayList<>();

    private List<String> risks = new ArrayList<>();

    private Boolean clarificationNeeded = Boolean.FALSE;

    private List<String> clarificationQuestions = new ArrayList<>();

    private Map<String, Object> timeRange = new HashMap<>();

    private String rawOutput;
}
