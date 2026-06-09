package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

@Data
public class DbQueryFilterCondition {

    private String op;

    private Object value;
}
