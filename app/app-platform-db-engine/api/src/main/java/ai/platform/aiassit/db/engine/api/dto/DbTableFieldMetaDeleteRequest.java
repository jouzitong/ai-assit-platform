package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

@Data
public class DbTableFieldMetaDeleteRequest {

    private String sourceKey;

    private String tableName;

    private String columnName;
}
