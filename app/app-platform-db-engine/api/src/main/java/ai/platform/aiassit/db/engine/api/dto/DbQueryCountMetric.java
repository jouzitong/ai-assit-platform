package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

@Data
public class DbQueryCountMetric {

    private String field;

    private String func;

    private String alias;
}
