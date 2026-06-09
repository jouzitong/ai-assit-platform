package ai.platform.aiassit.db.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DbQueryTreeExt {

    private List<DbQueryRelation> relations = new ArrayList<>();

    @JsonProperty("id_field")
    private String idField;

    @JsonProperty("parent_field")
    private String parentField;

    @JsonProperty("label_field")
    private String labelField;

    @JsonProperty("children_field")
    private String childrenField = "children";

    @JsonProperty("root_value")
    private Object rootValue;

    @JsonProperty("max_depth")
    private Integer maxDepth;
}
