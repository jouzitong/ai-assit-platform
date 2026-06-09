package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class DbQueryTreeResponse {

    private List<DbQueryTreeNode> records = new ArrayList<>();

    private Map<String, Object> summary = new LinkedHashMap<>();
}
