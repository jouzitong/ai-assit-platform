package ai.platform.aiassit.db.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DbQueryGetRequest {

    private String title;

    private String model;

    private Object id;

    @JsonProperty("filter_dict")
    private Map<String, Object> filterDict = new LinkedHashMap<>();

    private String filterExpr;

    private DbQueryGetExt ext = new DbQueryGetExt();
}
