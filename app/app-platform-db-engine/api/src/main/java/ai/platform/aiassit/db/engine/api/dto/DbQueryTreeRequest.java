package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class DbQueryTreeRequest {

    private String title;

    private String model;

    private Map<String, DbQueryFilterCondition> filters = new LinkedHashMap<>();

    private List<String> fields = new ArrayList<>();

    private List<DbQueryCountMetric> metrics = new ArrayList<>();

    private Map<String, DbQueryFilterCondition> having = new LinkedHashMap<>();

    private List<DbQuerySort> sorts = new ArrayList<>();

    private DbQueryTreeExt ext = new DbQueryTreeExt();
}
