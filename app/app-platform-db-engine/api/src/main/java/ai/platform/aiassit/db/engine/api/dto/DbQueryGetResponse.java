package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DbQueryGetResponse {

    private Map<String, Object> record = new LinkedHashMap<>();
}
