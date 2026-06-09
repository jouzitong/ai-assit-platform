package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class DbQueryPivotResponse {

    private List<String> columnKeys = new ArrayList<>();

    private List<Map<String, Object>> records = new ArrayList<>();

    private Map<String, Object> summary = new LinkedHashMap<>();
}
