package ai.platform.aiassit.data.virtualization.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class TransformPreviewResponse {
    private String transformerCode;
    private Integer transformerVersion;
    private Map<String, Object> outputs = new LinkedHashMap<>();
    private List<String> warnings = new ArrayList<>();
}
