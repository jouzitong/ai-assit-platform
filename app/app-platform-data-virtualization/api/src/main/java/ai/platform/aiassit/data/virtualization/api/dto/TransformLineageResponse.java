package ai.platform.aiassit.data.virtualization.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TransformLineageResponse {
    private List<Edge> edges = new ArrayList<>();

    @Data
    public static class Edge {
        private String sourceType;
        private String source;
        private String ruleCode;
        private String targetType;
        private String target;
    }
}
