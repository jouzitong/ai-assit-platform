package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DbQueryGetRequest {

    private String title;

    private String model;

    private Object id;

    private Map<String, DbQueryFilterCondition> filters = new LinkedHashMap<>();

    private DbQueryGetExt ext = new DbQueryGetExt();
}
