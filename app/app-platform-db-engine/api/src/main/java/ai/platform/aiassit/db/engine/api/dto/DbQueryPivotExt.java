package ai.platform.aiassit.db.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DbQueryPivotExt {

    private List<DbQueryRelation> relations = new ArrayList<>();

    @JsonProperty("fill_value")
    private Object fillValue;

    @JsonProperty("top_n")
    private Integer topN;

    @JsonProperty("time_grain")
    private String timeGrain;
}
