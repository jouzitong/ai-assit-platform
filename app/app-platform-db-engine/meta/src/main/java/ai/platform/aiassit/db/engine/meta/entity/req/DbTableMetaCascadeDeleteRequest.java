package ai.platform.aiassit.db.engine.meta.entity.req;

import lombok.Data;

@Data
public class DbTableMetaCascadeDeleteRequest {

    private String sourceKey;

    private String tableName;
}
