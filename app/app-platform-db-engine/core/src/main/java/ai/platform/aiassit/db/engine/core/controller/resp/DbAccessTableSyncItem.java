package ai.platform.aiassit.db.engine.core.controller.resp;

import lombok.Data;

@Data
public class DbAccessTableSyncItem {

    private String tableName;

    private Boolean tableCreated;

    private Boolean tableUpdated;

    private Integer fieldCreatedCount;

    private Integer fieldUpdatedCount;

    private Integer remoteFieldCount;

    private Integer indexCreatedCount;

    private Integer indexUpdatedCount;

    private Integer remoteIndexCount;
}
