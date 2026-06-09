package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DbQueryRelation {

    private String model;

    private String type;

    private Map<String, String> on = new LinkedHashMap<>();
}
