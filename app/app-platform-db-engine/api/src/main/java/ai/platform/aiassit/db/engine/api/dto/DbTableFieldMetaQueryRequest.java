package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

@Data
public class DbTableFieldMetaQueryRequest {

    private String sourceKey;

    private String tableName;

    private String columnName;
}
