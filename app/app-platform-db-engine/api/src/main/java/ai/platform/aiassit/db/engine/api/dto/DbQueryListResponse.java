package ai.platform.aiassit.db.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class DbQueryListResponse {

    private Long total = 0L;

    private Integer page = 1;

    @JsonProperty("page_size")
    private Integer pageSize = 10;

    private List<Map<String, Object>> records = new ArrayList<>();

    private Map<String, Object> summary = new LinkedHashMap<>();
}
