package ai.platform.aiassit.db.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DbQueryCountExt {

    private List<DbQueryRelation> relations = new ArrayList<>();

    @JsonProperty("time_grain")
    private String timeGrain;

    @JsonProperty("top_n")
    private Integer topN;
}
