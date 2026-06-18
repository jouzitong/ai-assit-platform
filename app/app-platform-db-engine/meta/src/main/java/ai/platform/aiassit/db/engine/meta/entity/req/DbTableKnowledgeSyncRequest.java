package ai.platform.aiassit.db.engine.meta.entity.req;

import lombok.Data;

@Data
public class DbTableKnowledgeSyncRequest {

    private String sourceKey;

    private String tableName;
}
