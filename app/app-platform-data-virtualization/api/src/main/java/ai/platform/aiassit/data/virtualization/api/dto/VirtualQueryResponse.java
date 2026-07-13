package ai.platform.aiassit.data.virtualization.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class VirtualQueryResponse {
    private String requestId;
    private String planId;
    private Long catalogVersion;
    private List<Map<String, Object>> records = new ArrayList<>();
    private Long total = 0L;
    private Map<String, Object> summary = new LinkedHashMap<>();
    private Integer physicalTaskCount;
    private Long executionMs;
}
