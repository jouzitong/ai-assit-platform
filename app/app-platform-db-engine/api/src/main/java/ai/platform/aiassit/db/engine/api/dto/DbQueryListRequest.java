package ai.platform.aiassit.db.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DbQueryListRequest {

    private String title;

    private String model;

    @JsonProperty("filter_dict")
    private Map<String, DbQueryFilterCondition> filterDict = new LinkedHashMap<>();

    private DbQueryExt ext = new DbQueryExt();

    private Integer page = 1;

    @JsonProperty("page_size")
    private Integer pageSize = 10;
}
