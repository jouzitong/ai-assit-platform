package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

@Data
public class DbQueryCountDimension {

    private String field;

    private String alias;
}
