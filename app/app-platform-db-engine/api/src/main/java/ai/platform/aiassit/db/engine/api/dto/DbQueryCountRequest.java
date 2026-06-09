package ai.platform.aiassit.db.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class DbQueryCountRequest {

    private String title;

    private String model;

    private Map<String, DbQueryFilterCondition> filters = new LinkedHashMap<>();

    private List<DbQueryCountDimension> dimensions = new ArrayList<>();

    private List<DbQueryCountMetric> metrics = new ArrayList<>();

    private Map<String, DbQueryFilterCondition> having = new LinkedHashMap<>();

    private List<DbQuerySort> sorts = new ArrayList<>();

    private Integer page = 1;

    @JsonProperty("page_size")
    private Integer pageSize = 10;

    private DbQueryCountExt ext = new DbQueryCountExt();
}
